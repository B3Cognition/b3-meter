const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const http = require('http');
const path = require('path');

const GRPC_PORT = process.env.GRPC_PORT || 50051;
const HTTP_PORT = process.env.HTTP_PORT || 8085;

// Load proto definition
const PROTO_PATH = path.join(__dirname, 'greeter.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const greeterProto = grpc.loadPackageDefinition(packageDefinition).greeter;

// Track request counts for observability
let grpcRequestCount = 0;

// --- gRPC service handlers ---

function sayHello(call, callback) {
  grpcRequestCount++;
  const name = call.request.name || 'World';
  callback(null, { message: `Hello ${name}` });
}

function sayHelloServerStream(call) {
  grpcRequestCount++;
  const name = call.request.name || 'World';
  const greetings = [
    `Hello ${name}`,
    `Greetings ${name}`,
    `Hi there ${name}`,
    `Hey ${name}`,
    `Welcome ${name}`,
  ];

  let index = 0;
  const interval = setInterval(() => {
    if (index >= greetings.length) {
      clearInterval(interval);
      call.end();
      return;
    }
    call.write({ message: greetings[index] });
    index++;
  }, 200);

  call.on('cancelled', () => {
    clearInterval(interval);
  });
}

function healthCheck(call, callback) {
  grpcRequestCount++;
  callback(null, { status: 'SERVING' });
}

// --- Start gRPC server ---

function startGrpcServer() {
  const server = new grpc.Server();
  server.addService(greeterProto.Greeter.service, {
    SayHello: sayHello,
    SayHelloServerStream: sayHelloServerStream,
    HealthCheck: healthCheck,
  });

  server.bindAsync(
    `0.0.0.0:${GRPC_PORT}`,
    grpc.ServerCredentials.createInsecure(),
    (err, port) => {
      if (err) {
        console.error('gRPC server failed to bind:', err);
        process.exit(1);
      }
      console.log(`gRPC server listening on port ${port}`);
    }
  );

  return server;
}

// --- Start HTTP health server ---

function startHttpHealthServer() {
  const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        status: 'ok',
        uptime: process.uptime(),
        grpcRequests: grpcRequestCount,
      }));
      return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
  });

  server.listen(HTTP_PORT, () => {
    console.log(`HTTP health endpoint listening on port ${HTTP_PORT}`);
  });

  return server;
}

// --- Main ---

const grpcServer = startGrpcServer();
const httpServer = startHttpHealthServer();

// Graceful shutdown
function shutdown() {
  console.log('Shutting down...');
  grpcServer.tryShutdown(() => {
    httpServer.close(() => {
      process.exit(0);
    });
  });
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
