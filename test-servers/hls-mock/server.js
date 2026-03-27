const http = require('http');
const crypto = require('crypto');

const PORT = process.env.PORT || 8084;
const SEGMENT_DURATION = 2; // seconds
const SEGMENTS_PER_PLAYLIST = 5;
const SEGMENT_SIZE = parseInt(process.env.SEGMENT_SIZE || '1024', 10); // bytes

// Track media sequence number — increments every SEGMENT_DURATION seconds
function getMediaSequence() {
  return Math.floor(Date.now() / (SEGMENT_DURATION * 1000));
}

const QUALITIES = {
  '720p': { bandwidth: 2800000, resolution: '1280x720' },
  '480p': { bandwidth: 1400000, resolution: '854x480' },
  '360p': { bandwidth: 800000, resolution: '640x360' },
};

function masterPlaylist() {
  let m3u8 = '#EXTM3U\n';
  m3u8 += '#EXT-X-VERSION:3\n';
  for (const [quality, info] of Object.entries(QUALITIES)) {
    m3u8 += `#EXT-X-STREAM-INF:BANDWIDTH=${info.bandwidth},RESOLUTION=${info.resolution},NAME="${quality}"\n`;
    m3u8 += `/live/${quality}/media.m3u8\n`;
  }
  return m3u8;
}

function mediaPlaylist(quality) {
  const seq = getMediaSequence();
  let m3u8 = '#EXTM3U\n';
  m3u8 += '#EXT-X-VERSION:3\n';
  m3u8 += `#EXT-X-TARGETDURATION:${SEGMENT_DURATION}\n`;
  m3u8 += `#EXT-X-MEDIA-SEQUENCE:${seq}\n`;

  for (let i = 0; i < SEGMENTS_PER_PLAYLIST; i++) {
    m3u8 += `#EXTINF:${SEGMENT_DURATION}.000,\n`;
    m3u8 += `/live/${quality}/segment-${seq + i}.ts\n`;
  }
  return m3u8;
}

function generateSegment(quality, segNum) {
  // Generate deterministic pseudo-random bytes based on quality + segment number
  const seed = `${quality}-${segNum}`;
  const hash = crypto.createHash('sha256').update(seed).digest();
  const buf = Buffer.alloc(SEGMENT_SIZE);
  for (let i = 0; i < SEGMENT_SIZE; i++) {
    buf[i] = hash[i % hash.length];
  }
  return buf;
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const path = url.pathname;

  // Health check
  if (req.method === 'GET' && path === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      uptime: process.uptime(),
      mediaSequence: getMediaSequence(),
    }));
    return;
  }

  // Master playlist
  if (req.method === 'GET' && path === '/live/master.m3u8') {
    res.writeHead(200, {
      'Content-Type': 'application/vnd.apple.mpegurl',
      'Cache-Control': 'no-cache',
    });
    res.end(masterPlaylist());
    return;
  }

  // Media playlist: /live/:quality/media.m3u8
  const mediaMatch = path.match(/^\/live\/(720p|480p|360p)\/media\.m3u8$/);
  if (req.method === 'GET' && mediaMatch) {
    const quality = mediaMatch[1];
    res.writeHead(200, {
      'Content-Type': 'application/vnd.apple.mpegurl',
      'Cache-Control': 'no-cache',
    });
    res.end(mediaPlaylist(quality));
    return;
  }

  // Segment: /live/:quality/segment-:n.ts
  const segMatch = path.match(/^\/live\/(720p|480p|360p)\/segment-(\d+)\.ts$/);
  if (req.method === 'GET' && segMatch) {
    const quality = segMatch[1];
    const segNum = parseInt(segMatch[2], 10);
    const data = generateSegment(quality, segNum);
    res.writeHead(200, {
      'Content-Type': 'video/MP2T',
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
  console.log(`HLS mock server listening on port ${PORT}`);
});
