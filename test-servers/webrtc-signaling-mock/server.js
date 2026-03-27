/**
 * WebRTC SDP Signaling mock server for load-testing WebRTCSampler.
 *
 * - POST /offer — accepts an SDP offer body, returns an SDP answer with ICE candidates.
 * - GET /health — returns {"status":"ok"}.
 *
 * Port: process.env.PORT || 8088
 */

'use strict';

const http = require('http');

const PORT = parseInt(process.env.PORT || '8088', 10);

/**
 * Generates a deterministic SDP answer.
 * Includes a mock ICE candidate pointing at the STUN mock on 127.0.0.1:3478.
 *
 * @param {string} _offerSdp - incoming SDP offer (unused; mock always returns same answer)
 * @returns {string} SDP answer string
 */
function generateSdpAnswer(_offerSdp) {
  const sessionId = Date.now();
  return [
    'v=0',
    `o=- ${sessionId} 0 IN IP4 127.0.0.1`,
    's=-',
    't=0 0',
    'm=audio 9 UDP/TLS/RTP/SAVPF 111',
    'c=IN IP4 0.0.0.0',
    'a=rtpmap:111 opus/48000/2',
    'a=fmtp:111 minptime=10;useinbandfec=1',
    'a=ice-ufrag:mock',
    'a=ice-pwd:mockpassword12345678901234',
    'a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00',
    'a=setup:active',
    'a=mid:0',
    'a=sendrecv',
    'a=rtcp-mux',
    'a=candidate:1 1 udp 2130706431 127.0.0.1 3478 typ host',
    'a=candidate:2 1 udp 1694498815 127.0.0.1 3478 typ srflx raddr 0.0.0.0 rport 0',
    'a=end-of-candidates',
    '',
  ].join('\r\n');
}

const server = http.createServer((req, res) => {
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }

  if (req.method === 'POST' && req.url === '/offer') {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      if (!body.trim()) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Empty SDP offer body' }));
        return;
      }

      const answer = generateSdpAnswer(body);
      res.writeHead(200, { 'Content-Type': 'application/sdp' });
      res.end(answer);
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'not found' }));
});

server.listen(PORT, () => {
  console.log(`WebRTC signaling mock server listening on port ${PORT}`);
});

/* ─── Graceful shutdown ─── */

function shutdown() {
  console.log('Shutting down WebRTC signaling mock...');
  server.close();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
