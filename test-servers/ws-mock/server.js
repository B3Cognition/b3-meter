const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8082;
const BROADCAST_INTERVAL_MS = parseInt(process.env.BROADCAST_INTERVAL_MS || '1000', 10);

const httpServer = http.createServer();
const wss = new WebSocketServer({ server: httpServer });

let globalSeq = 0;

// Track connected clients
const clients = new Set();

// Per-client subscription timers
const clientTimers = new Map();

wss.on('connection', (ws) => {
  clients.add(ws);
  console.log(`Client connected. Total: ${clients.size}`);

  // Send welcome message
  ws.send(JSON.stringify({
    type: 'welcome',
    message: 'Connected to WebSocket mock server',
    timestamp: Date.now(),
  }));

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      ws.send(JSON.stringify({ type: 'error', message: 'Invalid JSON' }));
      return;
    }

    if (msg.event === 'subscribe') {
      // Start broadcasting updates to this client
      const url = msg.url || 'default';
      console.log(`Client subscribed to: ${url}`);

      ws.send(JSON.stringify({
        type: 'subscribed',
        url,
        timestamp: Date.now(),
      }));

      // Clear any existing timer for this client
      if (clientTimers.has(ws)) {
        clearInterval(clientTimers.get(ws));
      }

      const timer = setInterval(() => {
        if (ws.readyState === ws.OPEN) {
          globalSeq++;
          ws.send(JSON.stringify({
            type: 'update',
            seq: globalSeq,
            ts: Date.now(),
            data: { value: Math.random() },
          }));
        }
      }, BROADCAST_INTERVAL_MS);

      clientTimers.set(ws, timer);

    } else if (msg.event === 'ping' || msg.type === 'ping') {
      ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));

    } else if (msg.event === 'unsubscribe') {
      if (clientTimers.has(ws)) {
        clearInterval(clientTimers.get(ws));
        clientTimers.delete(ws);
      }
      ws.send(JSON.stringify({ type: 'unsubscribed', timestamp: Date.now() }));

    } else {
      // Echo unknown messages
      ws.send(JSON.stringify({ type: 'echo', received: msg, timestamp: Date.now() }));
    }
  });

  ws.on('close', () => {
    clients.delete(ws);
    if (clientTimers.has(ws)) {
      clearInterval(clientTimers.get(ws));
      clientTimers.delete(ws);
    }
    console.log(`Client disconnected. Total: ${clients.size}`);
  });

  ws.on('error', (err) => {
    console.error('WebSocket error:', err.message);
  });
});

// Health endpoint via plain HTTP
httpServer.on('request', (req, res) => {
  if (req.url === '/health' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      clients: clients.size,
      uptime: process.uptime(),
      globalSeq,
    }));
  } else if (!req.headers.upgrade) {
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
  }
});

httpServer.listen(PORT, () => {
  console.log(`WebSocket mock server listening on port ${PORT}`);
});
