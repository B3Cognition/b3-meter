const net = require('net');
const http = require('http');

const SMTP_PORT = parseInt(process.env.SMTP_PORT || '9025', 10);
const HEALTH_PORT = parseInt(process.env.SMTP_HEALTH_PORT || '9026', 10);

// In-memory store of received messages
const messages = [];

// SMTP state machine states
const STATE = {
  CONNECTED: 'CONNECTED',
  EHLO: 'EHLO',
  MAIL: 'MAIL',
  RCPT: 'RCPT',
  DATA: 'DATA',
  QUIT: 'QUIT',
};

function createSession() {
  return {
    state: STATE.CONNECTED,
    from: null,
    to: [],
    dataLines: [],
    collectingData: false,
  };
}

const smtpServer = net.createServer((socket) => {
  console.log('SMTP connection accepted');
  const session = createSession();

  // Send banner
  socket.write('220 b3meter-smtp-mock ESMTP ready\r\n');

  let lineBuffer = '';

  socket.on('data', (chunk) => {
    lineBuffer += chunk.toString();

    let newlineIdx;
    while ((newlineIdx = lineBuffer.indexOf('\n')) !== -1) {
      const rawLine = lineBuffer.substring(0, newlineIdx);
      lineBuffer = lineBuffer.substring(newlineIdx + 1);
      const line = rawLine.replace(/\r$/, '');

      handleLine(socket, session, line);
    }
  });

  socket.on('close', () => {
    console.log('SMTP connection closed');
  });

  socket.on('error', (err) => {
    console.error('SMTP socket error:', err.message);
  });
});

function handleLine(socket, session, line) {
  // In DATA collection mode, accumulate lines until lone '.'
  if (session.collectingData) {
    if (line === '.') {
      // End of DATA
      const body = session.dataLines.join('\n');
      messages.push({
        from: session.from,
        to: session.to.slice(),
        body,
        receivedAt: new Date().toISOString(),
      });
      console.log(`Message stored. Total: ${messages.length}`);
      session.collectingData = false;
      session.state = STATE.MAIL; // ready for another MAIL or QUIT
      socket.write('250 Message accepted for delivery\r\n');
    } else {
      // RFC 5321: leading dot doubling — strip one leading dot
      session.dataLines.push(line.startsWith('..') ? line.substring(1) : line);
    }
    return;
  }

  const upper = line.toUpperCase();

  if (upper.startsWith('EHLO') || upper.startsWith('HELO')) {
    session.state = STATE.EHLO;
    const domain = line.substring(5).trim() || 'client';
    socket.write(`250-b3meter-smtp-mock Hello ${domain}\r\n`);
    socket.write('250-SIZE 10240000\r\n');
    socket.write('250-8BITMIME\r\n');
    socket.write('250 OK\r\n');

  } else if (upper.startsWith('MAIL FROM:')) {
    if (session.state !== STATE.EHLO && session.state !== STATE.MAIL) {
      socket.write('503 Bad sequence of commands\r\n');
      return;
    }
    const match = line.match(/<([^>]*)>/);
    session.from = match ? match[1] : line.substring(10).trim();
    session.to = [];
    session.dataLines = [];
    session.state = STATE.MAIL;
    socket.write('250 OK\r\n');

  } else if (upper.startsWith('RCPT TO:')) {
    if (session.state !== STATE.MAIL && session.state !== STATE.RCPT) {
      socket.write('503 Bad sequence of commands\r\n');
      return;
    }
    const match = line.match(/<([^>]*)>/);
    const to = match ? match[1] : line.substring(8).trim();
    session.to.push(to);
    session.state = STATE.RCPT;
    socket.write('250 OK\r\n');

  } else if (upper === 'DATA') {
    if (session.state !== STATE.RCPT) {
      socket.write('503 Bad sequence of commands\r\n');
      return;
    }
    session.state = STATE.DATA;
    session.collectingData = true;
    session.dataLines = [];
    socket.write('354 Start mail input; end with <CRLF>.<CRLF>\r\n');

  } else if (upper === 'RSET') {
    session.from = null;
    session.to = [];
    session.dataLines = [];
    session.collectingData = false;
    session.state = STATE.EHLO;
    socket.write('250 OK\r\n');

  } else if (upper === 'NOOP') {
    socket.write('250 OK\r\n');

  } else if (upper === 'QUIT') {
    session.state = STATE.QUIT;
    socket.write('221 Bye\r\n');
    socket.end();

  } else {
    socket.write('500 Command not recognized\r\n');
  }
}

smtpServer.listen(SMTP_PORT, () => {
  console.log(`SMTP mock server listening on port ${SMTP_PORT}`);
});

// HTTP health + messages API
const healthServer = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      messages: messages.length,
      uptime: process.uptime(),
    }));
  } else if (req.method === 'GET' && req.url === '/messages') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(messages));
  } else if (req.method === 'DELETE' && req.url === '/messages') {
    messages.length = 0;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ cleared: true }));
  } else {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
  }
});

healthServer.listen(HEALTH_PORT, () => {
  console.log(`SMTP mock health server listening on port ${HEALTH_PORT}`);
});

process.on('SIGTERM', () => {
  smtpServer.close();
  healthServer.close();
  process.exit(0);
});
