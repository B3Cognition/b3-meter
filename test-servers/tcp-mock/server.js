const net = require('net');
const http = require('http');

const TCP_PORT = parseInt(process.env.TCP_PORT || '9089', 10);
const HEALTH_PORT = parseInt(process.env.TCP_HEALTH_PORT || '9090', 10);
const ECHO_TIMEOUT_MS = 5000;

let connectionCount = 0;
let activeConnections = 0;

// TCP echo server
const tcpServer = net.createServer((socket) => {
  connectionCount++;
  activeConnections++;
  console.log(`TCP connection accepted. Active: ${activeConnections}`);

  let buffer = '';
  let timer = null;

  const closeConnection = () => {
    clearTimeout(timer);
    socket.destroy();
  };

  // 5-second idle timeout
  timer = setTimeout(() => {
    console.log('TCP connection timed out (no newline received within 5s)');
    closeConnection();
  }, ECHO_TIMEOUT_MS);

  socket.on('data', (chunk) => {
    buffer += chunk.toString();

    const newlineIdx = buffer.indexOf('\n');
    if (newlineIdx !== -1) {
      // Echo the data up to and including the newline
      const line = buffer.substring(0, newlineIdx + 1);
      clearTimeout(timer);
      socket.write(line, () => {
        socket.end();
      });
    }
  });

  socket.on('close', () => {
    activeConnections--;
    clearTimeout(timer);
    console.log(`TCP connection closed. Active: ${activeConnections}`);
  });

  socket.on('error', (err) => {
    activeConnections--;
    clearTimeout(timer);
    console.error('TCP socket error:', err.message);
  });
});

tcpServer.listen(TCP_PORT, () => {
  console.log(`TCP echo server listening on port ${TCP_PORT}`);
});

// HTTP health server
const healthServer = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      connections: activeConnections,
      totalConnections: connectionCount,
      uptime: process.uptime(),
    }));
  } else {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
  }
});

healthServer.listen(HEALTH_PORT, () => {
  console.log(`TCP mock health server listening on port ${HEALTH_PORT}`);
});

process.on('SIGTERM', () => {
  tcpServer.close();
  healthServer.close();
  process.exit(0);
});
