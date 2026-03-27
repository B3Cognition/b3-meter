const ldap = require('ldapjs');

const PORT = parseInt(process.env.PORT || '3389', 10);

// Sample directory data
const directory = {
  'dc=example,dc=org': {
    dn: 'dc=example,dc=org',
    attributes: {
      dc: 'example',
      objectClass: ['top', 'domain'],
    },
  },
  'cn=admin,dc=example,dc=org': {
    dn: 'cn=admin,dc=example,dc=org',
    attributes: {
      cn: 'admin',
      objectClass: ['top', 'person', 'organizationalPerson'],
      userPassword: 'admin',
      description: 'LDAP Administrator',
    },
  },
  'uid=jdoe,ou=people,dc=example,dc=org': {
    dn: 'uid=jdoe,ou=people,dc=example,dc=org',
    attributes: {
      uid: 'jdoe',
      cn: 'John Doe',
      sn: 'Doe',
      givenName: 'John',
      mail: 'jdoe@example.com',
      objectClass: ['top', 'person', 'organizationalPerson', 'inetOrgPerson'],
      userPassword: 'password123',
    },
  },
  'uid=asmith,ou=people,dc=example,dc=org': {
    dn: 'uid=asmith,ou=people,dc=example,dc=org',
    attributes: {
      uid: 'asmith',
      cn: 'Alice Smith',
      sn: 'Smith',
      givenName: 'Alice',
      mail: 'asmith@example.com',
      objectClass: ['top', 'person', 'organizationalPerson', 'inetOrgPerson'],
      userPassword: 'alice456',
    },
  },
  'uid=bwilson,ou=people,dc=example,dc=org': {
    dn: 'uid=bwilson,ou=people,dc=example,dc=org',
    attributes: {
      uid: 'bwilson',
      cn: 'Bob Wilson',
      sn: 'Wilson',
      givenName: 'Bob',
      mail: 'bwilson@example.com',
      objectClass: ['top', 'person', 'organizationalPerson', 'inetOrgPerson'],
      userPassword: 'bob789',
    },
  },
  'ou=people,dc=example,dc=org': {
    dn: 'ou=people,dc=example,dc=org',
    attributes: {
      ou: 'people',
      objectClass: ['top', 'organizationalUnit'],
    },
  },
  'ou=groups,dc=example,dc=org': {
    dn: 'ou=groups,dc=example,dc=org',
    attributes: {
      ou: 'groups',
      objectClass: ['top', 'organizationalUnit'],
    },
  },
};

// Valid bind credentials
const validCredentials = {
  'cn=admin,dc=example,dc=org': 'admin',
  'uid=jdoe,ou=people,dc=example,dc=org': 'password123',
  'uid=asmith,ou=people,dc=example,dc=org': 'alice456',
  'uid=bwilson,ou=people,dc=example,dc=org': 'bob789',
};

const server = ldap.createServer();

// Bind handler — authentication
server.bind('dc=example,dc=org', (req, res, next) => {
  const dn = req.dn.toString();
  const password = req.credentials;

  console.log(`BIND dn="${dn}"`);

  const expectedPassword = validCredentials[dn];
  if (expectedPassword && expectedPassword === password) {
    res.end();
    return next();
  }

  return next(new ldap.InvalidCredentialsError('Invalid credentials'));
});

// Search handler
server.search('dc=example,dc=org', (req, res, next) => {
  const baseDN = req.dn.toString();
  const filter = req.filter;

  console.log(`SEARCH base="${baseDN}" scope=${req.scope} filter="${filter}"`);

  const entries = Object.values(directory);

  for (const entry of entries) {
    // Check if entry is under the search base
    if (!entry.dn.endsWith(baseDN) && entry.dn !== baseDN) {
      continue;
    }

    // Apply scope filtering
    if (req.scope === 'base' && entry.dn !== baseDN) {
      continue;
    }
    if (req.scope === 'one') {
      // One level below base
      const entryDn = ldap.parseDN(entry.dn);
      const searchDn = ldap.parseDN(baseDN);
      if (entryDn.rdns.length !== searchDn.rdns.length + 1) {
        continue;
      }
    }

    // Apply filter matching
    if (filter.matches(entry.attributes)) {
      res.send({
        dn: entry.dn,
        attributes: entry.attributes,
      });
    }
  }

  res.end();
  return next();
});

// Add handler
server.add('dc=example,dc=org', (req, res, next) => {
  const dn = req.dn.toString();
  console.log(`ADD dn="${dn}"`);

  if (directory[dn]) {
    return next(new ldap.EntryAlreadyExistsError(dn));
  }

  const attrs = {};
  req.toObject().attributes.forEach((attr) => {
    attrs[attr.type] = attr.vals.length === 1 ? attr.vals[0] : attr.vals;
  });

  directory[dn] = { dn, attributes: attrs };
  res.end();
  return next();
});

// Modify handler
server.modify('dc=example,dc=org', (req, res, next) => {
  const dn = req.dn.toString();
  console.log(`MODIFY dn="${dn}"`);

  if (!directory[dn]) {
    return next(new ldap.NoSuchObjectError(dn));
  }

  for (const change of req.changes) {
    const mod = change.modification;
    switch (change.operation) {
      case 'replace':
        directory[dn].attributes[mod.type] =
          mod.vals.length === 1 ? mod.vals[0] : mod.vals;
        break;
      case 'add':
        const existing = directory[dn].attributes[mod.type];
        if (Array.isArray(existing)) {
          directory[dn].attributes[mod.type] = existing.concat(mod.vals);
        } else if (existing) {
          directory[dn].attributes[mod.type] = [existing].concat(mod.vals);
        } else {
          directory[dn].attributes[mod.type] =
            mod.vals.length === 1 ? mod.vals[0] : mod.vals;
        }
        break;
      case 'delete':
        delete directory[dn].attributes[mod.type];
        break;
    }
  }

  res.end();
  return next();
});

// Delete handler
server.del('dc=example,dc=org', (req, res, next) => {
  const dn = req.dn.toString();
  console.log(`DELETE dn="${dn}"`);

  if (!directory[dn]) {
    return next(new ldap.NoSuchObjectError(dn));
  }

  delete directory[dn];
  res.end();
  return next();
});

server.listen(PORT, () => {
  console.log(`LDAP mock server listening on port ${PORT}`);
  console.log(`Entries: ${Object.keys(directory).length}`);
  console.log(`Valid bind DNs: ${Object.keys(validCredentials).join(', ')}`);
});

// Health check via HTTP on PORT+1
const http = require('http');
const healthPort = PORT + 1;

const healthServer = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      service: 'ldap-mock',
      port: PORT,
      entries: Object.keys(directory).length,
    }));
  } else {
    res.writeHead(404);
    res.end('Not Found');
  }
});

healthServer.listen(healthPort, () => {
  console.log(`Health endpoint at http://localhost:${healthPort}/health`);
});
