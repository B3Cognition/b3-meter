const express = require('express');

const app = express();
const PORT = process.env.PORT || 8081;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Request counter for metrics
let requestCount = 0;
app.use((req, res, next) => {
  requestCount++;
  next();
});

// Configurable latency (ms) and error rate (0-1)
const config = {
  latency: parseInt(process.env.LATENCY_MS || '0', 10),
  errorRate: parseFloat(process.env.ERROR_RATE || '0'),
};

function applyLatency(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// GET /health — always 200
app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime(), requests: requestCount });
});

// GET /api/users — returns JSON array, configurable latency
app.get('/api/users', async (req, res) => {
  const latency = parseInt(req.query.latency || config.latency, 10);
  if (latency > 0) {
    await applyLatency(latency);
  }

  res.json([
    { id: 1, name: 'Alice', email: 'alice@example.com' },
    { id: 2, name: 'Bob', email: 'bob@example.com' },
    { id: 3, name: 'Charlie', email: 'charlie@example.com' },
    { id: 4, name: 'Diana', email: 'diana@example.com' },
    { id: 5, name: 'Eve', email: 'eve@example.com' },
  ]);
});

// POST /api/data — echoes body, configurable error rate
app.post('/api/data', async (req, res) => {
  const latency = parseInt(req.query.latency || config.latency, 10);
  if (latency > 0) {
    await applyLatency(latency);
  }

  const errorRate = parseFloat(req.query.error_rate || config.errorRate);
  if (errorRate > 0 && Math.random() < errorRate) {
    return res.status(500).json({ error: 'Simulated server error', rate: errorRate });
  }

  res.json({
    received: req.body,
    timestamp: Date.now(),
    headers: {
      'content-type': req.get('content-type'),
    },
  });
});

// GET /slow — configurable delay via query param
app.get('/slow', async (req, res) => {
  const delay = parseInt(req.query.delay || '3000', 10);
  await applyLatency(delay);
  res.json({ delayed: true, delay_ms: delay, timestamp: Date.now() });
});

// GET /error — returns 500
app.get('/error', (req, res) => {
  res.status(500).json({ error: 'Internal Server Error', timestamp: Date.now() });
});

// ANY /status/:code — returns that status code
app.all('/status/:code', (req, res) => {
  const code = parseInt(req.params.code, 10);
  if (code < 100 || code > 599) {
    return res.status(400).json({ error: 'Invalid status code', code });
  }
  res.status(code).json({
    status: code,
    method: req.method,
    timestamp: Date.now(),
  });
});

app.listen(PORT, () => {
  console.log(`HTTP mock server listening on port ${PORT}`);
});
