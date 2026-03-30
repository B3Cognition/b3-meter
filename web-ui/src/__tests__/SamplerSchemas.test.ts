// Copyright 2024-2026 b3meter Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
import { describe, it, expect } from 'vitest';
import { jdbcSamplerSchema, jdbcSamplerDefaults } from '../components/PropertyPanel/schemas/jdbcSamplerSchema.js';
import { jmsSamplerSchema, jmsSamplerDefaults } from '../components/PropertyPanel/schemas/jmsSamplerSchema.js';
import { tcpSamplerSchema, tcpSamplerDefaults } from '../components/PropertyPanel/schemas/tcpSamplerSchema.js';
import { ldapSamplerSchema, ldapSamplerDefaults } from '../components/PropertyPanel/schemas/ldapSamplerSchema.js';
import { smtpSamplerSchema, smtpSamplerDefaults } from '../components/PropertyPanel/schemas/smtpSamplerSchema.js';
import { ftpSamplerSchema, ftpSamplerDefaults } from '../components/PropertyPanel/schemas/ftpSamplerSchema.js';
import { boltSamplerSchema, boltSamplerDefaults } from '../components/PropertyPanel/schemas/boltSamplerSchema.js';
import { schemaRegistry, getSchemaEntry } from '../components/PropertyPanel/schemas/index.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Returns the first error message for the given field path. */
function firstError(result: { success: boolean; error?: { issues: Array<{ path: (string | number)[]; message: string }> } }, path: string): string | undefined {
  if (result.success) return undefined;
  const issue = result.error?.issues.find((i) => i.path[0] === path);
  return issue?.message;
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

describe('schemaRegistry', () => {
  it('registers all 9 node types', () => {
    const expectedTypes = [
      'ThreadGroup',
      'HTTPSampler',
      'JDBCSampler',
      'JMSSampler',
      'TCPSampler',
      'LDAPSampler',
      'SMTPSampler',
      'FTPSampler',
      'BoltSampler',
    ];
    for (const type of expectedTypes) {
      expect(schemaRegistry[type], `${type} should be in registry`).toBeDefined();
    }
  });

  it('getSchemaEntry returns undefined for unknown types', () => {
    expect(getSchemaEntry('UnknownType')).toBeUndefined();
  });

  it('getSchemaEntry returns a schema and defaults for JDBCSampler', () => {
    const entry = getSchemaEntry('JDBCSampler');
    expect(entry).toBeDefined();
    expect(entry?.schema).toBeDefined();
    expect(entry?.defaults).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// JDBCSampler
// ---------------------------------------------------------------------------

describe('jdbcSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'myDS' });
    expect(result.success).toBe(true);
  });
});

describe('jdbcSamplerSchema — invalid inputs', () => {
  it('rejects empty dataSource', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'dataSource')).toBe('Data source is required');
  });

  it('rejects invalid queryType', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'ds', queryType: 'invalid' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'queryType')).toBe('Must be select, update, or callable');
  });

  it('rejects negative queryTimeout', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'ds', queryTimeout: -1 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'queryTimeout')).toBe('Must be at least 0');
  });

  it('rejects maxRows of 0 (below minimum)', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'ds', maxRows: 0 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'maxRows')).toBe('Must be between 1 and 100000');
  });

  it('rejects maxRows above 100000', () => {
    const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'ds', maxRows: 100_001 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'maxRows')).toBe('Must be between 1 and 100000');
  });

  it('accepts all valid queryType values', () => {
    for (const qt of ['select', 'update', 'callable'] as const) {
      const result = jdbcSamplerSchema.safeParse({ ...jdbcSamplerDefaults, dataSource: 'ds', queryType: qt });
      expect(result.success, `queryType '${qt}' should be valid`).toBe(true);
    }
  });
});

// ---------------------------------------------------------------------------
// JMSSampler
// ---------------------------------------------------------------------------

describe('jmsSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = jmsSamplerSchema.safeParse({
      ...jmsSamplerDefaults,
      providerUrl: 'tcp://localhost:61616',
      connectionFactory: 'ConnectionFactory',
      destination: 'queue/test',
    });
    expect(result.success).toBe(true);
  });
});

describe('jmsSamplerSchema — invalid inputs', () => {
  const base = {
    providerUrl: 'tcp://localhost:61616',
    connectionFactory: 'CF',
    destination: 'queue/test',
    messageType: 'text' as const,
    content: '',
    jndiProperties: '',
  };

  it('rejects empty providerUrl', () => {
    const result = jmsSamplerSchema.safeParse({ ...base, providerUrl: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'providerUrl')).toBe('Provider URL is required');
  });

  it('rejects empty connectionFactory', () => {
    const result = jmsSamplerSchema.safeParse({ ...base, connectionFactory: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'connectionFactory')).toBe('Connection factory is required');
  });

  it('rejects empty destination', () => {
    const result = jmsSamplerSchema.safeParse({ ...base, destination: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'destination')).toBe('Destination is required');
  });

  it('rejects invalid messageType', () => {
    const result = jmsSamplerSchema.safeParse({ ...base, messageType: 'xml' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'messageType')).toBe('Must be text, object, map, or bytes');
  });

  it('accepts all valid messageType values', () => {
    for (const mt of ['text', 'object', 'map', 'bytes'] as const) {
      const result = jmsSamplerSchema.safeParse({ ...base, messageType: mt });
      expect(result.success, `messageType '${mt}' should be valid`).toBe(true);
    }
  });
});

// ---------------------------------------------------------------------------
// TCPSampler
// ---------------------------------------------------------------------------

describe('tcpSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = tcpSamplerSchema.safeParse({ ...tcpSamplerDefaults, server: 'localhost' });
    expect(result.success).toBe(true);
  });
});

describe('tcpSamplerSchema — invalid inputs', () => {
  const base = { ...tcpSamplerDefaults, server: 'localhost' };

  it('rejects empty server', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, server: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'server')).toBe('Server is required');
  });

  it('rejects port 0 (below minimum)', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, port: 0 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects port 65536 (above maximum)', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, port: 65_536 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects negative timeout', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, timeout: -1 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'timeout')).toBe('Must be at least 0');
  });

  it('rejects eolByte below -1', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, eolByte: -2 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'eolByte')).toBe('Must be -1 (disabled) or 0–255');
  });

  it('rejects eolByte above 255', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, eolByte: 256 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'eolByte')).toBe('Must be -1 (disabled) or 0–255');
  });

  it('accepts eolByte of -1 (disabled)', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, eolByte: -1 });
    expect(result.success).toBe(true);
  });

  it('accepts eolByte of 10 (newline)', () => {
    const result = tcpSamplerSchema.safeParse({ ...base, eolByte: 10 });
    expect(result.success).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// LDAPSampler
// ---------------------------------------------------------------------------

describe('ldapSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = ldapSamplerSchema.safeParse({
      ...ldapSamplerDefaults,
      serverUrl: 'ldap://localhost:389',
      searchBase: 'dc=example,dc=com',
    });
    expect(result.success).toBe(true);
  });
});

describe('ldapSamplerSchema — invalid inputs', () => {
  const base = {
    ...ldapSamplerDefaults,
    serverUrl: 'ldap://localhost:389',
    searchBase: 'dc=example,dc=com',
  };

  it('rejects empty serverUrl', () => {
    const result = ldapSamplerSchema.safeParse({ ...base, serverUrl: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'serverUrl')).toBe('Server URL is required');
  });

  it('rejects empty searchBase', () => {
    const result = ldapSamplerSchema.safeParse({ ...base, searchBase: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'searchBase')).toBe('Search base is required');
  });

  it('rejects empty searchFilter', () => {
    const result = ldapSamplerSchema.safeParse({ ...base, searchFilter: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'searchFilter')).toBe('Search filter is required');
  });

  it('rejects invalid scope', () => {
    const result = ldapSamplerSchema.safeParse({ ...base, scope: 'deep' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'scope')).toBe('Must be base, one, or sub');
  });

  it('rejects invalid authType', () => {
    const result = ldapSamplerSchema.safeParse({ ...base, authType: 'digest' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'authType')).toBe('Must be simple or none');
  });

  it('accepts all valid scope values', () => {
    for (const scope of ['base', 'one', 'sub'] as const) {
      const result = ldapSamplerSchema.safeParse({ ...base, scope });
      expect(result.success, `scope '${scope}' should be valid`).toBe(true);
    }
  });
});

// ---------------------------------------------------------------------------
// SMTPSampler
// ---------------------------------------------------------------------------

describe('smtpSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = smtpSamplerSchema.safeParse({
      ...smtpSamplerDefaults,
      server: 'smtp.example.com',
      from: 'sender@example.com',
      to: 'recipient@example.com',
    });
    expect(result.success).toBe(true);
  });
});

describe('smtpSamplerSchema — invalid inputs', () => {
  const base = {
    ...smtpSamplerDefaults,
    server: 'smtp.example.com',
    from: 'sender@example.com',
    to: 'recipient@example.com',
  };

  it('rejects empty server', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, server: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'server')).toBe('Server is required');
  });

  it('rejects port 0 (below minimum)', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, port: 0 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects port 99999 (above maximum)', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, port: 99_999 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects invalid from email address', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, from: 'not-an-email' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'from')).toBe('Must be a valid email address');
  });

  it('rejects empty to field', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, to: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'to')).toBe('Recipient is required');
  });

  it('accepts useSSL and useStartTLS as booleans', () => {
    const result = smtpSamplerSchema.safeParse({ ...base, useSSL: true, useStartTLS: true });
    expect(result.success).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// FTPSampler
// ---------------------------------------------------------------------------

describe('ftpSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = ftpSamplerSchema.safeParse({
      ...ftpSamplerDefaults,
      server: 'ftp.example.com',
      remotePath: '/upload/file.txt',
      localPath: '/tmp/file.txt',
    });
    expect(result.success).toBe(true);
  });
});

describe('ftpSamplerSchema — invalid inputs', () => {
  const base = {
    ...ftpSamplerDefaults,
    server: 'ftp.example.com',
    remotePath: '/upload/file.txt',
    localPath: '/tmp/file.txt',
  };

  it('rejects empty server', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, server: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'server')).toBe('Server is required');
  });

  it('rejects port 0 (below minimum)', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, port: 0 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects port 65536 (above maximum)', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, port: 65_536 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'port')).toBe('Must be between 1 and 65535');
  });

  it('rejects empty remotePath', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, remotePath: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'remotePath')).toBe('Remote path is required');
  });

  it('rejects empty localPath', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, localPath: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'localPath')).toBe('Local path is required');
  });

  it('rejects invalid mode', () => {
    const result = ftpSamplerSchema.safeParse({ ...base, mode: 'delete' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'mode')).toBe('Must be get or put');
  });

  it('accepts mode "get" and "put"', () => {
    for (const mode of ['get', 'put'] as const) {
      const result = ftpSamplerSchema.safeParse({ ...base, mode });
      expect(result.success, `mode '${mode}' should be valid`).toBe(true);
    }
  });
});

// ---------------------------------------------------------------------------
// BoltSampler (Neo4j)
// ---------------------------------------------------------------------------

describe('boltSamplerSchema — valid defaults', () => {
  it('accepts valid default values', () => {
    const result = boltSamplerSchema.safeParse({
      ...boltSamplerDefaults,
      serverUrl: 'bolt://localhost:7687',
      cypherQuery: 'MATCH (n) RETURN n LIMIT 10',
    });
    expect(result.success).toBe(true);
  });
});

describe('boltSamplerSchema — invalid inputs', () => {
  const base = {
    ...boltSamplerDefaults,
    serverUrl: 'bolt://localhost:7687',
    cypherQuery: 'MATCH (n) RETURN n',
  };

  it('rejects empty serverUrl', () => {
    const result = boltSamplerSchema.safeParse({ ...base, serverUrl: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'serverUrl')).toBe('Server URL is required');
  });

  it('rejects empty cypherQuery', () => {
    const result = boltSamplerSchema.safeParse({ ...base, cypherQuery: '' });
    expect(result.success).toBe(false);
    expect(firstError(result, 'cypherQuery')).toBe('Cypher query is required');
  });

  it('rejects negative timeout', () => {
    const result = boltSamplerSchema.safeParse({ ...base, timeout: -1 });
    expect(result.success).toBe(false);
    expect(firstError(result, 'timeout')).toBe('Must be at least 0');
  });

  it('accepts timeout of 0 (no timeout)', () => {
    const result = boltSamplerSchema.safeParse({ ...base, timeout: 0 });
    expect(result.success).toBe(true);
  });

  it('accepts optional database name', () => {
    const result = boltSamplerSchema.safeParse({ ...base, database: 'neo4j' });
    expect(result.success).toBe(true);
  });
});
