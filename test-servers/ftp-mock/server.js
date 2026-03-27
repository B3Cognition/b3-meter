const net = require('net');

const PORT = parseInt(process.env.PORT || '2121', 10);

// In-memory file system for the mock
const fileSystem = {
  '/pub/readme.txt': 'Welcome to the FTP mock server.\nThis is a test file for E2E testing.',
  '/pub/data.csv': 'id,name,value\n1,alpha,100\n2,beta,200\n3,gamma,300',
  '/pub/large.bin': 'A'.repeat(1024), // 1KB binary-like file
  '/uploads/': null, // directory marker
};

// Track uploaded files
const uploads = new Map();

// Directory listings
const directoryListings = {
  '/pub': [
    '-rw-r--r--    1 ftp      ftp            64 Mar 26 12:00 readme.txt',
    '-rw-r--r--    1 ftp      ftp            58 Mar 26 12:00 data.csv',
    '-rw-r--r--    1 ftp      ftp          1024 Mar 26 12:00 large.bin',
  ].join('\r\n') + '\r\n',
  '/uploads': '',
  '/': [
    'drwxr-xr-x    2 ftp      ftp          4096 Mar 26 12:00 pub',
    'drwxr-xr-x    2 ftp      ftp          4096 Mar 26 12:00 uploads',
  ].join('\r\n') + '\r\n',
  '': [
    'drwxr-xr-x    2 ftp      ftp          4096 Mar 26 12:00 pub',
    'drwxr-xr-x    2 ftp      ftp          4096 Mar 26 12:00 uploads',
  ].join('\r\n') + '\r\n',
};

function createDataServer(callback) {
  return new Promise((resolve) => {
    const dataServer = net.createServer((dataSocket) => {
      callback(dataSocket, () => {
        dataServer.close();
      });
    });
    dataServer.listen(0, '127.0.0.1', () => {
      const addr = dataServer.address();
      resolve({ server: dataServer, port: addr.port });
    });
  });
}

function formatPasvResponse(port) {
  const p1 = Math.floor(port / 256);
  const p2 = port % 256;
  return `227 Entering Passive Mode (127,0,0,1,${p1},${p2})\r\n`;
}

const server = net.createServer((socket) => {
  let currentDir = '/';
  let authenticated = false;
  let username = '';
  let pendingDataHandler = null;
  let binaryMode = false;

  // Send welcome banner
  socket.write('220 jmeter-next FTP mock server ready\r\n');

  let buffer = '';

  socket.on('data', (data) => {
    buffer += data.toString('utf8');

    let lineEnd;
    while ((lineEnd = buffer.indexOf('\r\n')) !== -1) {
      const line = buffer.substring(0, lineEnd);
      buffer = buffer.substring(lineEnd + 2);
      handleCommand(line);
    }
    // Also handle LF-only line endings
    while ((lineEnd = buffer.indexOf('\n')) !== -1) {
      const line = buffer.substring(0, lineEnd).replace(/\r$/, '');
      buffer = buffer.substring(lineEnd + 1);
      handleCommand(line);
    }
  });

  function handleCommand(line) {
    const spaceIdx = line.indexOf(' ');
    const cmd = spaceIdx === -1 ? line.toUpperCase() : line.substring(0, spaceIdx).toUpperCase();
    const arg = spaceIdx === -1 ? '' : line.substring(spaceIdx + 1);

    console.log(`CMD: ${cmd} ${arg}`);

    switch (cmd) {
      case 'USER':
        username = arg || 'anonymous';
        socket.write('331 Please specify the password\r\n');
        break;

      case 'PASS':
        authenticated = true;
        socket.write(`230 Login successful (user: ${username})\r\n`);
        break;

      case 'SYST':
        socket.write('215 UNIX Type: L8\r\n');
        break;

      case 'FEAT':
        socket.write('211-Features:\r\n PASV\r\n UTF8\r\n211 End\r\n');
        break;

      case 'PWD':
        socket.write(`257 "${currentDir}" is the current directory\r\n`);
        break;

      case 'CWD':
        currentDir = arg.startsWith('/') ? arg : currentDir + '/' + arg;
        socket.write(`250 Directory changed to ${currentDir}\r\n`);
        break;

      case 'TYPE':
        binaryMode = arg.toUpperCase() === 'I';
        socket.write(`200 Type set to ${arg.toUpperCase()}\r\n`);
        break;

      case 'PASV':
        handlePasv(socket);
        break;

      case 'LIST':
        handleList(socket, arg);
        break;

      case 'RETR':
        handleRetr(socket, arg);
        break;

      case 'STOR':
        handleStor(socket, arg);
        break;

      case 'DELE':
        socket.write(`250 Deleted ${arg}\r\n`);
        break;

      case 'MKD':
        socket.write(`257 "${arg}" created\r\n`);
        break;

      case 'RMD':
        socket.write(`250 Directory removed\r\n`);
        break;

      case 'NOOP':
        socket.write('200 NOOP ok\r\n');
        break;

      case 'QUIT':
        socket.write('221 Goodbye\r\n');
        socket.end();
        break;

      default:
        socket.write(`502 Command "${cmd}" not implemented\r\n`);
    }
  }

  async function handlePasv(ctrlSocket) {
    try {
      const { server: dataServer, port } = await createDataServer((dataSocket, closeServer) => {
        // Store the data socket for the pending command
        if (pendingDataHandler) {
          pendingDataHandler(dataSocket, closeServer);
          pendingDataHandler = null;
        } else {
          // No pending handler yet — store connection for later use
          pendingDataHandler = { dataSocket, closeServer };
        }
      });

      // Store the data server reference
      pendingDataHandler = null;

      ctrlSocket.write(formatPasvResponse(port));

      // Set up data handler to be used by LIST/RETR/STOR
      const dataPromise = new Promise((resolve) => {
        const prevHandler = pendingDataHandler;
        pendingDataHandler = (handler) => {
          // When a data transfer command is issued, it calls this
          const dataServerConn = net.createConnection(port, '127.0.0.1', () => {
            // This won't work — we need to wait for the client to connect
          });
        };
      });

      // Simpler approach: store the data server and let commands use it
      socket._dataServer = dataServer;
      socket._dataPort = port;
    } catch (err) {
      ctrlSocket.write('425 Cannot open data connection\r\n');
      console.error('PASV error:', err.message);
    }
  }

  function handleList(ctrlSocket, path) {
    const listPath = path || currentDir;
    const listing = directoryListings[listPath] || directoryListings['/'];

    ctrlSocket.write('150 Opening data connection for directory listing\r\n');

    // Wait a tiny bit for the data connection, then send data
    const dataServer = net.createServer((dataSocket) => {
      dataSocket.write(listing, () => {
        dataSocket.end(() => {
          dataServer.close();
          ctrlSocket.write('226 Transfer complete\r\n');
        });
      });
    });

    dataServer.listen(socket._dataPort, '127.0.0.1');
  }

  function handleRetr(ctrlSocket, filePath) {
    const fullPath = filePath.startsWith('/') ? filePath : currentDir + '/' + filePath;
    const content = fileSystem[fullPath] || uploads.get(fullPath);

    if (!content) {
      ctrlSocket.write(`550 ${filePath}: No such file\r\n`);
      return;
    }

    ctrlSocket.write(`150 Opening data connection for ${filePath}\r\n`);

    const dataServer = net.createServer((dataSocket) => {
      dataSocket.write(content, () => {
        dataSocket.end(() => {
          dataServer.close();
          ctrlSocket.write('226 Transfer complete\r\n');
        });
      });
    });

    dataServer.listen(socket._dataPort, '127.0.0.1');
  }

  function handleStor(ctrlSocket, filePath) {
    const fullPath = filePath.startsWith('/') ? filePath : currentDir + '/' + filePath;

    ctrlSocket.write(`150 Opening data connection for ${filePath}\r\n`);

    const chunks = [];
    const dataServer = net.createServer((dataSocket) => {
      dataSocket.on('data', (chunk) => {
        chunks.push(chunk);
      });
      dataSocket.on('end', () => {
        const content = Buffer.concat(chunks).toString('utf8');
        uploads.set(fullPath, content);
        fileSystem[fullPath] = content;
        dataServer.close();
        ctrlSocket.write('226 Transfer complete\r\n');
        console.log(`STOR: saved ${content.length} bytes to ${fullPath}`);
      });
    });

    dataServer.listen(socket._dataPort, '127.0.0.1');
  }

  socket.on('close', () => {
    console.log('Client disconnected');
  });

  socket.on('error', (err) => {
    console.error('Socket error:', err.message);
  });
});

// Health check via simple HTTP on PORT+1
const http = require('http');
const healthPort = PORT + 1;

const healthServer = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      service: 'ftp-mock',
      port: PORT,
      files: Object.keys(fileSystem).length,
      uploads: uploads.size,
    }));
  } else {
    res.writeHead(404);
    res.end('Not Found');
  }
});

server.listen(PORT, () => {
  console.log(`FTP mock server listening on port ${PORT}`);
  console.log(`Files available: ${Object.keys(fileSystem).filter(k => fileSystem[k] !== null).join(', ')}`);
});

healthServer.listen(healthPort, () => {
  console.log(`Health endpoint at http://localhost:${healthPort}/health`);
});
