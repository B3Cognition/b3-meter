const http = require('http');
const crypto = require('crypto');

const PORT = process.env.PORT || 8086;
const INIT_SEGMENT_SIZE = 1024;  // 1KB fake init segment
const MEDIA_SEGMENT_SIZE = 2048; // 2KB fake media segment

// Track request counts
let requestCount = 0;

// DASH MPD manifest (static, 60s presentation with 2s segments)
const MPD_XML = `<?xml version="1.0"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT60S"
     minBufferTime="PT2S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
  <Period>
    <AdaptationSet mimeType="video/mp4" segmentAlignment="true">
      <Representation id="720p" bandwidth="2000000" width="1280" height="720" codecs="avc1.64001f">
        <SegmentTemplate media="720p/segment-$Number$.m4s" initialization="720p/init.mp4"
                         startNumber="0" duration="2000" timescale="1000"/>
      </Representation>
      <Representation id="480p" bandwidth="1000000" width="854" height="480" codecs="avc1.4d401e">
        <SegmentTemplate media="480p/segment-$Number$.m4s" initialization="480p/init.mp4"
                         startNumber="0" duration="2000" timescale="1000"/>
      </Representation>
    </AdaptationSet>
  </Period>
</MPD>`;

function generateFakeSegment(seed, size) {
  const hash = crypto.createHash('sha256').update(seed).digest();
  const buf = Buffer.alloc(size);
  for (let i = 0; i < size; i++) {
    buf[i] = hash[i % hash.length];
  }
  return buf;
}

const VALID_QUALITIES = ['720p', '480p'];
const MAX_SEGMENT_NUMBER = 29; // 60s / 2s per segment = 30 segments (0-29)

const server = http.createServer((req, res) => {
  requestCount++;
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const pathName = url.pathname;

  // Health check
  if (req.method === 'GET' && pathName === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      uptime: process.uptime(),
      requests: requestCount,
    }));
    return;
  }

  // DASH MPD manifest
  if (req.method === 'GET' && pathName === '/live/manifest.mpd') {
    res.writeHead(200, {
      'Content-Type': 'application/dash+xml',
      'Cache-Control': 'no-cache',
      'Content-Length': Buffer.byteLength(MPD_XML),
    });
    res.end(MPD_XML);
    return;
  }

  // Init segment: /live/:quality/init.mp4
  const initMatch = pathName.match(/^\/live\/(720p|480p)\/init\.mp4$/);
  if (req.method === 'GET' && initMatch) {
    const quality = initMatch[1];
    const data = generateFakeSegment(`init-${quality}`, INIT_SEGMENT_SIZE);
    res.writeHead(200, {
      'Content-Type': 'video/mp4',
      'Content-Length': data.length,
      'Cache-Control': 'max-age=86400',
    });
    res.end(data);
    return;
  }

  // Media segment: /live/:quality/segment-:n.m4s
  const segMatch = pathName.match(/^\/live\/(720p|480p)\/segment-(\d+)\.m4s$/);
  if (req.method === 'GET' && segMatch) {
    const quality = segMatch[1];
    const segNum = parseInt(segMatch[2], 10);
    if (segNum > MAX_SEGMENT_NUMBER) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Segment out of range', max: MAX_SEGMENT_NUMBER }));
      return;
    }
    const data = generateFakeSegment(`${quality}-seg-${segNum}`, MEDIA_SEGMENT_SIZE);
    res.writeHead(200, {
      'Content-Type': 'video/mp4',
      'Content-Length': data.length,
      'Cache-Control': 'max-age=3600',
    });
    res.end(data);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not found' }));
});

server.listen(PORT, () => {
  console.log(`DASH mock server listening on port ${PORT}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('Shutting down...');
  server.close(() => process.exit(0));
});
process.on('SIGINT', () => {
  console.log('Shutting down...');
  server.close(() => process.exit(0));
});
