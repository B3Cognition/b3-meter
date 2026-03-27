const http = require('http');

const PORT = process.env.PORT || 8083;

let globalId = 0;

function sseHeaders(res) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });
}

function sendEvent(res, data, eventName, id) {
  if (id !== undefined) {
    res.write(`id: ${id}\n`);
  }
  if (eventName) {
    res.write(`event: ${eventName}\n`);
  }
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

function startStream(res, intervalMs, useNamedEvents) {
  sseHeaders(res);

  const timer = setInterval(() => {
    globalId++;
    const payload = {
      timestamp: Date.now(),
      value: Math.random(),
    };

    if (useNamedEvents) {
      // Alternate between different named events
      const events = ['metric', 'counter', 'gauge', 'histogram'];
      const eventName = events[globalId % events.length];
      sendEvent(res, { ...payload, metric_type: eventName }, eventName, globalId);
    } else {
      sendEvent(res, payload, null, globalId);
    }
  }, intervalMs);

  // Send initial event immediately
  globalId++;
  const initial = { timestamp: Date.now(), value: Math.random(), initial: true };
  if (useNamedEvents) {
    sendEvent(res, { ...initial, metric_type: 'metric' }, 'metric', globalId);
  } else {
    sendEvent(res, initial, null, globalId);
  }

  res.on('close', () => {
    clearInterval(timer);
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  if (req.method === 'GET' && url.pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      uptime: process.uptime(),
      eventsEmitted: globalId,
    }));
    return;
  }

  if (req.method === 'GET' && url.pathname === '/events') {
    // Standard SSE stream — 1 event per second
    startStream(res, 1000, false);
    return;
  }

  if (req.method === 'GET' && url.pathname === '/events/fast') {
    // Fast SSE stream — every 100ms
    startStream(res, 100, false);
    return;
  }

  if (req.method === 'GET' && url.pathname === '/events/named') {
    // Named events — 1 per second
    startStream(res, 1000, true);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, () => {
  console.log(`SSE mock server listening on port ${PORT}`);
});
