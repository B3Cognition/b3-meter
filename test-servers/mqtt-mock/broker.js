const net = require('net');

const PORT = process.env.PORT || 1883;

// Topic → Set<socket>
const subscriptions = new Map();
// Topic → last published message (Buffer) for retained messages
const retained = new Map();
// Track connected clients
const clients = new Set();

// MQTT packet types
const CONNECT = 1;
const CONNACK = 2;
const PUBLISH = 3;
const PUBACK = 4;
const SUBSCRIBE = 8;
const SUBACK = 9;
const UNSUBSCRIBE = 10;
const UNSUBACK = 11;
const PINGREQ = 12;
const PINGRESP = 13;
const DISCONNECT = 14;

function decodeRemainingLength(buf, offset) {
  let multiplier = 1;
  let value = 0;
  let pos = offset;
  let byte;
  do {
    if (pos >= buf.length) return { length: -1, bytesUsed: 0 };
    byte = buf[pos++];
    value += (byte & 0x7f) * multiplier;
    multiplier *= 128;
  } while ((byte & 0x80) !== 0);
  return { length: value, bytesUsed: pos - offset };
}

function encodeRemainingLength(length) {
  const bytes = [];
  do {
    let byte = length % 128;
    length = Math.floor(length / 128);
    if (length > 0) byte |= 0x80;
    bytes.push(byte);
  } while (length > 0);
  return Buffer.from(bytes);
}

function readUTF8String(buf, offset) {
  if (offset + 2 > buf.length) return { str: '', bytesUsed: 0 };
  const len = buf.readUInt16BE(offset);
  const str = buf.toString('utf8', offset + 2, offset + 2 + len);
  return { str, bytesUsed: 2 + len };
}

function writeUTF8String(str) {
  const strBuf = Buffer.from(str, 'utf8');
  const lenBuf = Buffer.alloc(2);
  lenBuf.writeUInt16BE(strBuf.length, 0);
  return Buffer.concat([lenBuf, strBuf]);
}

function buildPacket(type, flags, payload) {
  const header = Buffer.from([(type << 4) | (flags & 0x0f)]);
  const remaining = encodeRemainingLength(payload.length);
  return Buffer.concat([header, remaining, payload]);
}

function handleConnect(socket, payload) {
  // Simply accept all connections
  // CONNACK: session present = 0, return code = 0 (accepted)
  const connack = buildPacket(CONNACK, 0, Buffer.from([0x00, 0x00]));
  socket.write(connack);
  console.log(`Client connected from ${socket.remoteAddress}:${socket.remotePort}`);
}

function handlePublish(socket, firstByte, payload) {
  const qos = (firstByte >> 1) & 0x03;
  const retain = firstByte & 0x01;
  let offset = 0;

  // Read topic
  const { str: topic, bytesUsed: topicBytes } = readUTF8String(payload, offset);
  offset += topicBytes;

  // Read packet ID for QoS > 0
  let packetId = 0;
  if (qos > 0 && offset + 2 <= payload.length) {
    packetId = payload.readUInt16BE(offset);
    offset += 2;
  }

  // Message payload
  const message = payload.slice(offset);

  console.log(`PUBLISH topic="${topic}" qos=${qos} retain=${retain} len=${message.length}`);

  // Store retained message
  if (retain) {
    if (message.length > 0) {
      retained.set(topic, message);
    } else {
      retained.delete(topic);
    }
  }

  // Forward to subscribers (exact topic match)
  const subs = subscriptions.get(topic);
  if (subs) {
    const pubPayload = Buffer.concat([writeUTF8String(topic), message]);
    const pubPacket = buildPacket(PUBLISH, 0, pubPayload);
    for (const sub of subs) {
      if (sub !== socket && sub.writable) {
        sub.write(pubPacket);
      }
    }
  }

  // Send PUBACK for QoS 1
  if (qos === 1 && packetId > 0) {
    const ackPayload = Buffer.alloc(2);
    ackPayload.writeUInt16BE(packetId, 0);
    socket.write(buildPacket(PUBACK, 0, ackPayload));
  }
}

function handleSubscribe(socket, payload) {
  let offset = 0;

  // Packet ID
  const packetId = payload.readUInt16BE(offset);
  offset += 2;

  const grantedQos = [];

  // Read topic filters
  while (offset < payload.length) {
    const { str: topic, bytesUsed } = readUTF8String(payload, offset);
    offset += bytesUsed;
    const requestedQos = payload[offset++] || 0;

    console.log(`SUBSCRIBE topic="${topic}" qos=${requestedQos}`);

    // Register subscription
    if (!subscriptions.has(topic)) {
      subscriptions.set(topic, new Set());
    }
    subscriptions.get(topic).add(socket);

    // Grant QoS 0 always (simplified broker)
    grantedQos.push(0x00);

    // Deliver retained message if exists
    const retainedMsg = retained.get(topic);
    if (retainedMsg) {
      const pubPayload = Buffer.concat([writeUTF8String(topic), retainedMsg]);
      const pubPacket = buildPacket(PUBLISH, 0x01, pubPayload); // retain flag set
      socket.write(pubPacket);
    }
  }

  // SUBACK
  const subackPayload = Buffer.alloc(2 + grantedQos.length);
  subackPayload.writeUInt16BE(packetId, 0);
  for (let i = 0; i < grantedQos.length; i++) {
    subackPayload[2 + i] = grantedQos[i];
  }
  socket.write(buildPacket(SUBACK, 0, subackPayload));
}

function handleUnsubscribe(socket, payload) {
  let offset = 0;
  const packetId = payload.readUInt16BE(offset);
  offset += 2;

  while (offset < payload.length) {
    const { str: topic, bytesUsed } = readUTF8String(payload, offset);
    offset += bytesUsed;

    const subs = subscriptions.get(topic);
    if (subs) {
      subs.delete(socket);
      if (subs.size === 0) subscriptions.delete(topic);
    }
    console.log(`UNSUBSCRIBE topic="${topic}"`);
  }

  const unsubackPayload = Buffer.alloc(2);
  unsubackPayload.writeUInt16BE(packetId, 0);
  socket.write(buildPacket(UNSUBACK, 0, unsubackPayload));
}

function handlePingReq(socket) {
  socket.write(buildPacket(PINGRESP, 0, Buffer.alloc(0)));
}

function removeClientSubscriptions(socket) {
  for (const [topic, subs] of subscriptions) {
    subs.delete(socket);
    if (subs.size === 0) subscriptions.delete(topic);
  }
}

const server = net.createServer((socket) => {
  clients.add(socket);
  let buffer = Buffer.alloc(0);

  socket.on('data', (data) => {
    buffer = Buffer.concat([buffer, data]);

    while (buffer.length >= 2) {
      const firstByte = buffer[0];
      const packetType = (firstByte >> 4) & 0x0f;
      const { length: remainingLength, bytesUsed } = decodeRemainingLength(buffer, 1);

      if (remainingLength < 0) break; // incomplete length encoding

      const totalLength = 1 + bytesUsed + remainingLength;
      if (buffer.length < totalLength) break; // incomplete packet

      const payload = buffer.slice(1 + bytesUsed, totalLength);
      buffer = buffer.slice(totalLength);

      try {
        switch (packetType) {
          case CONNECT:
            handleConnect(socket, payload);
            break;
          case PUBLISH:
            handlePublish(socket, firstByte, payload);
            break;
          case SUBSCRIBE:
            handleSubscribe(socket, payload);
            break;
          case UNSUBSCRIBE:
            handleUnsubscribe(socket, payload);
            break;
          case PINGREQ:
            handlePingReq(socket);
            break;
          case DISCONNECT:
            console.log('Client sent DISCONNECT');
            socket.end();
            break;
          default:
            console.log(`Unhandled packet type: ${packetType}`);
        }
      } catch (err) {
        console.error(`Error handling packet type ${packetType}:`, err.message);
      }
    }
  });

  socket.on('close', () => {
    clients.delete(socket);
    removeClientSubscriptions(socket);
    console.log(`Client disconnected. Total: ${clients.size}`);
  });

  socket.on('error', (err) => {
    console.error('Socket error:', err.message);
    clients.delete(socket);
    removeClientSubscriptions(socket);
  });
});

server.listen(PORT, () => {
  console.log(`MQTT mock broker listening on port ${PORT}`);
  console.log(`Active topics: ${subscriptions.size}, Retained messages: ${retained.size}`);
});

// HTTP health endpoint on a separate port (for health checks — MQTT is TCP-only)
const HEALTH_PORT = process.env.HEALTH_PORT || 1884;
const http = require('http');
http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      topics: subscriptions.size,
      clients: clients.size,
      retained: retained.size,
    }));
  } else {
    res.writeHead(404);
    res.end();
  }
}).listen(HEALTH_PORT, () => console.log(`MQTT health endpoint on port ${HEALTH_PORT}`));
