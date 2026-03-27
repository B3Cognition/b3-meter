/**
 * Minimal STUN mock server for WebRTC ICE candidate gathering tests.
 *
 * - UDP port 3478: accepts STUN Binding Request (0x0001), responds with
 *   STUN Binding Response (0x0101) containing XOR-MAPPED-ADDRESS.
 * - HTTP port 8087: GET /health returns {"status":"ok"}.
 *
 * RFC 5389 — Session Traversal Utilities for NAT (STUN).
 */

'use strict';

const dgram = require('dgram');
const http = require('http');
const crypto = require('crypto');

const STUN_PORT = parseInt(process.env.STUN_PORT || '3478', 10);
const HTTP_PORT = parseInt(process.env.PORT || '8087', 10);

/* ─── STUN constants ─── */

const STUN_MAGIC_COOKIE = 0x2112A442;
const STUN_MAGIC_COOKIE_BUF = Buffer.from([0x21, 0x12, 0xA4, 0x42]);

const STUN_BINDING_REQUEST  = 0x0001;
const STUN_BINDING_RESPONSE = 0x0101;

// Attribute types
const ATTR_XOR_MAPPED_ADDRESS = 0x0020;

/* ─── STUN helpers ─── */

/**
 * Parses a minimal STUN header (20 bytes).
 * Returns { type, length, magicCookie, transactionId } or null on invalid.
 */
function parseStunHeader(buf) {
  if (buf.length < 20) return null;
  const type = buf.readUInt16BE(0);
  const length = buf.readUInt16BE(2);
  const magicCookie = buf.readUInt32BE(4);
  const transactionId = buf.slice(8, 20);
  return { type, length, magicCookie, transactionId };
}

/**
 * Builds a STUN Binding Response with XOR-MAPPED-ADDRESS attribute.
 * @param {Buffer} transactionId - 12-byte transaction ID from request
 * @param {string} ip - remote IP address (IPv4 only)
 * @param {number} port - remote port
 */
function buildBindingResponse(transactionId, ip, port) {
  // XOR-MAPPED-ADDRESS value: 1 byte reserved, 1 byte family, 2 byte X-Port, 4 byte X-Address
  const family = 0x01; // IPv4
  const xPort = port ^ (STUN_MAGIC_COOKIE >>> 16);
  const ipParts = ip.split('.').map(Number);
  const ipInt = ((ipParts[0] << 24) | (ipParts[1] << 16) | (ipParts[2] << 8) | ipParts[3]) >>> 0;
  const xAddr = ipInt ^ STUN_MAGIC_COOKIE;

  const attrValue = Buffer.alloc(8);
  attrValue.writeUInt8(0, 0);        // reserved
  attrValue.writeUInt8(family, 1);    // family
  attrValue.writeUInt16BE(xPort, 2);  // X-Port
  attrValue.writeUInt32BE(xAddr, 4);  // X-Address

  // Attribute header: type (2) + length (2) + value (8) = 12 bytes
  const attrHeader = Buffer.alloc(4);
  attrHeader.writeUInt16BE(ATTR_XOR_MAPPED_ADDRESS, 0);
  attrHeader.writeUInt16BE(attrValue.length, 2);

  const attributes = Buffer.concat([attrHeader, attrValue]);

  // STUN header: type (2) + length (2) + magic cookie (4) + transaction ID (12) = 20 bytes
  const header = Buffer.alloc(20);
  header.writeUInt16BE(STUN_BINDING_RESPONSE, 0);
  header.writeUInt16BE(attributes.length, 2);
  STUN_MAGIC_COOKIE_BUF.copy(header, 4);
  transactionId.copy(header, 8);

  return Buffer.concat([header, attributes]);
}

/* ─── UDP STUN Server ─── */

const udpServer = dgram.createSocket('udp4');

udpServer.on('message', (msg, rinfo) => {
  const header = parseStunHeader(msg);
  if (!header) return;

  if (header.magicCookie !== STUN_MAGIC_COOKIE) return;

  if (header.type === STUN_BINDING_REQUEST) {
    const response = buildBindingResponse(header.transactionId, rinfo.address, rinfo.port);
    udpServer.send(response, rinfo.port, rinfo.address, (err) => {
      if (err) {
        console.error(`STUN send error: ${err.message}`);
      }
    });
  }
});

udpServer.on('error', (err) => {
  console.error(`STUN UDP error: ${err.message}`);
  udpServer.close();
});

udpServer.bind(STUN_PORT, () => {
  console.log(`STUN mock server listening on UDP port ${STUN_PORT}`);
});

/* ─── HTTP Health Endpoint ─── */

const httpServer = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'not found' }));
});

httpServer.listen(HTTP_PORT, () => {
  console.log(`STUN mock HTTP health endpoint on port ${HTTP_PORT}`);
});

/* ─── Graceful shutdown ─── */

function shutdown() {
  console.log('Shutting down STUN mock server...');
  udpServer.close();
  httpServer.close();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
