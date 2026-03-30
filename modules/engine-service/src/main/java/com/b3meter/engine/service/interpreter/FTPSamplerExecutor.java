package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes an {@code FTPSampler} {@link PlanNode} using raw TCP sockets.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code FTPSampler.host} — FTP server hostname</li>
 *   <li>{@code FTPSampler.port} — port (default 21)</li>
 *   <li>{@code FTPSampler.username} — username (default "anonymous")</li>
 *   <li>{@code FTPSampler.password} — password (default "")</li>
 *   <li>{@code FTPSampler.path} — remote file path</li>
 *   <li>{@code FTPSampler.action} — "list", "download", or "upload"</li>
 *   <li>{@code FTPSampler.localContent} — content to upload (for upload action)</li>
 *   <li>{@code FTPSampler.timeout} — timeout in ms (default 10000)</li>
 * </ul>
 *
 * <p>Implements the FTP command protocol over raw TCP sockets. No external FTP
 * library is used (Constitution Principle I: framework-free).
 */
public final class FTPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(FTPSamplerExecutor.class.getName());

    private static final int DEFAULT_PORT = 21;
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    /** Pattern matching PASV response: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2). */
    static final Pattern PASV_PATTERN = Pattern.compile(
            "\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");

    private FTPSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the FTP operation described by {@code node}.
     *
     * @param node      the FTPSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String host = resolve(node.getStringProp("FTPSampler.host", ""), variables);
        int port = node.getIntProp("FTPSampler.port", DEFAULT_PORT);
        String username = resolve(node.getStringProp("FTPSampler.username", "anonymous"), variables);
        String password = resolve(node.getStringProp("FTPSampler.password", ""), variables);
        String path = resolve(node.getStringProp("FTPSampler.path", ""), variables);
        String action = resolve(node.getStringProp("FTPSampler.action", "list"), variables).toLowerCase();
        String localContent = resolve(node.getStringProp("FTPSampler.localContent", ""), variables);
        int timeout = node.getIntProp("FTPSampler.timeout", DEFAULT_TIMEOUT_MS);

        if (host.isBlank()) {
            result.setFailureMessage("FTPSampler.host is empty");
            return;
        }

        if (path.isBlank() && !"list".equals(action)) {
            result.setFailureMessage("FTPSampler.path is empty");
            return;
        }

        LOG.log(Level.FINE, "FTPSamplerExecutor: {0} on {1}:{2} path={3}",
                new Object[]{action, host, port, path});

        long start = System.currentTimeMillis();

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // Read 220 welcome banner
            String welcome = readReply(reader);
            int welcomeCode = parseReplyCode(welcome);
            if (welcomeCode != 220) {
                result.setStatusCode(welcomeCode);
                result.setFailureMessage("FTP unexpected welcome: " + welcome);
                return;
            }

            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // USER
            sendCommand(out, "USER " + username);
            String userReply = readReply(reader);
            int userCode = parseReplyCode(userReply);
            if (userCode != 331 && userCode != 230) {
                result.setStatusCode(userCode);
                result.setFailureMessage("FTP USER failed: " + userReply);
                return;
            }

            // PASS (only if 331 — server expects password)
            if (userCode == 331) {
                sendCommand(out, "PASS " + password);
                String passReply = readReply(reader);
                int passCode = parseReplyCode(passReply);
                if (passCode != 230) {
                    result.setStatusCode(passCode);
                    result.setFailureMessage("FTP PASS failed: " + passReply);
                    return;
                }
            }

            // Execute action
            String responseBody;
            int replyCode;

            switch (action) {
                case "list" -> {
                    String[] listResult = executeList(socket, reader, out, path, host, timeout);
                    replyCode = parseReplyCode(listResult[0]);
                    responseBody = listResult[1];
                }
                case "download" -> {
                    String[] dlResult = executeDownload(socket, reader, out, path, host, timeout);
                    replyCode = parseReplyCode(dlResult[0]);
                    responseBody = dlResult[1];
                }
                case "upload" -> {
                    String[] ulResult = executeUpload(socket, reader, out, path, localContent, host, timeout);
                    replyCode = parseReplyCode(ulResult[0]);
                    responseBody = ulResult[1];
                }
                default -> {
                    result.setFailureMessage("FTP unsupported action: " + action);
                    return;
                }
            }

            result.setStatusCode(replyCode);
            result.setResponseBody(responseBody);

            // QUIT
            sendCommand(out, "QUIT");
            // Best-effort read of 221 reply; ignore errors
            try {
                readReply(reader);
            } catch (IOException ignored) {
                // server may close immediately
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setFailureMessage("FTP error: " + e.getMessage());
            LOG.log(Level.WARNING, "FTPSamplerExecutor: error for " + host + ":" + port, e);
        }
    }

    // =========================================================================
    // FTP action implementations
    // =========================================================================

    /**
     * Executes LIST via PASV data channel.
     *
     * @return array of [lastReplyLine, directoryListing]
     */
    private static String[] executeList(Socket ctrlSocket, BufferedReader reader,
                                         OutputStream out, String path,
                                         String host, int timeout) throws IOException {
        // Enter passive mode
        int[] dataAddr = enterPassive(reader, out, host);

        // Send LIST command
        String listPath = path.isBlank() ? "" : " " + path;
        sendCommand(out, "LIST" + listPath);

        // Read data from passive port
        String listing = readDataChannel(dataAddr[0], dataAddr[1], dataAddr[2], timeout);

        // Read 150 (transfer starting) and 226 (transfer complete) replies
        String reply = readReply(reader);
        int code = parseReplyCode(reply);
        if (code == 150 || code == 125) {
            reply = readReply(reader); // read 226
        }

        return new String[]{reply, listing};
    }

    /**
     * Executes RETR (download) via PASV data channel.
     *
     * @return array of [lastReplyLine, fileContent]
     */
    private static String[] executeDownload(Socket ctrlSocket, BufferedReader reader,
                                             OutputStream out, String path,
                                             String host, int timeout) throws IOException {
        // Set binary mode
        sendCommand(out, "TYPE I");
        readReply(reader);

        // Enter passive mode
        int[] dataAddr = enterPassive(reader, out, host);

        // Send RETR command
        sendCommand(out, "RETR " + path);

        // Read data
        String content = readDataChannel(dataAddr[0], dataAddr[1], dataAddr[2], timeout);

        // Read 150 + 226 replies
        String reply = readReply(reader);
        int code = parseReplyCode(reply);
        if (code == 150 || code == 125) {
            reply = readReply(reader);
        }

        return new String[]{reply, content};
    }

    /**
     * Executes STOR (upload) via PASV data channel.
     *
     * @return array of [lastReplyLine, statusMessage]
     */
    private static String[] executeUpload(Socket ctrlSocket, BufferedReader reader,
                                           OutputStream out, String path,
                                           String localContent, String host,
                                           int timeout) throws IOException {
        // Set binary mode
        sendCommand(out, "TYPE I");
        readReply(reader);

        // Enter passive mode
        int[] dataAddr = enterPassive(reader, out, host);

        // Send STOR command
        sendCommand(out, "STOR " + path);

        // Write data to passive port
        writeDataChannel(dataAddr[0], dataAddr[1], dataAddr[2],
                localContent.getBytes(StandardCharsets.UTF_8), timeout);

        // Read 150 + 226 replies
        String reply = readReply(reader);
        int code = parseReplyCode(reply);
        if (code == 150 || code == 125) {
            reply = readReply(reader);
        }

        return new String[]{reply, "Uploaded " + localContent.length() + " bytes to " + path};
    }

    // =========================================================================
    // FTP protocol helpers (package-visible for testing)
    // =========================================================================

    /**
     * Sends PASV command and parses the data channel address.
     *
     * @return int array: [ip-as-int (unused), port, combined] — actually [0]=host-packed, [1]=port, [2]=host-packed
     *         Simplified: returns {hostPart1<<24|..., port, 0} — actually returns
     *         a 3-element array where [0] is packed IP, [1] is port, [2] is unused.
     */
    private static int[] enterPassive(BufferedReader reader, OutputStream out,
                                       String fallbackHost) throws IOException {
        sendCommand(out, "PASV");
        String pasvReply = readReply(reader);
        int pasvCode = parseReplyCode(pasvReply);
        if (pasvCode != 227) {
            throw new IOException("PASV failed: " + pasvReply);
        }
        return parsePasvReply(pasvReply, fallbackHost);
    }

    /**
     * Parses a PASV reply to extract host and port for the data connection.
     *
     * <p>Format: {@code 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)}.
     * The data port is computed as {@code p1 * 256 + p2}.
     *
     * @param reply        the full PASV reply string
     * @param fallbackHost host to use if PASV reports 0.0.0.0 or internal IP
     * @return int array: [0] = unused, [1] = port, [2] = unused.
     *         The host string is embedded in the return value indirectly;
     *         callers should use the fallbackHost for connection.
     * @throws IOException if the reply cannot be parsed
     */
    static int[] parsePasvReply(String reply, String fallbackHost) throws IOException {
        Matcher m = PASV_PATTERN.matcher(reply);
        if (!m.find()) {
            throw new IOException("Cannot parse PASV reply: " + reply);
        }
        int p1 = Integer.parseInt(m.group(5));
        int p2 = Integer.parseInt(m.group(6));
        int port = p1 * 256 + p2;

        // Pack host parts for potential use, but we'll use fallbackHost for actual connection
        int h1 = Integer.parseInt(m.group(1));
        int h2 = Integer.parseInt(m.group(2));
        int h3 = Integer.parseInt(m.group(3));
        int h4 = Integer.parseInt(m.group(4));
        int packedHost = (h1 << 24) | (h2 << 16) | (h3 << 8) | h4;

        return new int[]{packedHost, port, 0};
    }

    /**
     * Reads data from a passive-mode data channel.
     */
    private static String readDataChannel(int packedHost, int port, int unused,
                                           int timeout) throws IOException {
        // Use localhost for data channel (mock servers run locally)
        String host = formatPackedHost(packedHost);
        try (Socket dataSocket = new Socket()) {
            dataSocket.setSoTimeout(timeout);
            dataSocket.connect(new InetSocketAddress(host, port), timeout);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = dataSocket.getInputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes data to a passive-mode data channel.
     */
    private static void writeDataChannel(int packedHost, int port, int unused,
                                          byte[] data, int timeout) throws IOException {
        String host = formatPackedHost(packedHost);
        try (Socket dataSocket = new Socket()) {
            dataSocket.setSoTimeout(timeout);
            dataSocket.connect(new InetSocketAddress(host, port), timeout);
            dataSocket.getOutputStream().write(data);
            dataSocket.getOutputStream().flush();
        }
    }

    /**
     * Formats a packed host integer back to dotted notation.
     */
    static String formatPackedHost(int packed) {
        return ((packed >> 24) & 0xFF) + "." +
               ((packed >> 16) & 0xFF) + "." +
               ((packed >> 8) & 0xFF) + "." +
               (packed & 0xFF);
    }

    /**
     * Sends an FTP command line (appends CRLF).
     */
    static void sendCommand(OutputStream out, String command) throws IOException {
        out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Reads a single FTP reply line. Handles multi-line replies (code-space vs code-dash).
     *
     * @return the final reply line
     */
    static String readReply(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
            // Multi-line reply: lines starting with "NNN-" continue; "NNN " ends
            if (line.length() >= 4 && Character.isDigit(line.charAt(0))
                    && Character.isDigit(line.charAt(1))
                    && Character.isDigit(line.charAt(2))) {
                if (line.charAt(3) == ' ' || line.charAt(3) == '\0') {
                    break; // final line of reply
                }
                // line.charAt(3) == '-' means continuation
                sb.append('\n');
            } else {
                break; // non-standard reply, return as-is
            }
        }
        if (line == null) {
            throw new IOException("FTP connection closed unexpectedly");
        }
        return sb.toString();
    }

    /**
     * Parses the 3-digit reply code from an FTP reply line.
     *
     * @param reply the reply string (e.g. "220 Welcome")
     * @return the numeric reply code, or -1 if unparseable
     */
    static int parseReplyCode(String reply) {
        if (reply == null || reply.length() < 3) return -1;
        try {
            return Integer.parseInt(reply.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Formats an FTP command string for the given action. Visible for testing.
     *
     * @param action  the action name (list, download, upload)
     * @param path    the remote file path
     * @return the FTP command string (without CRLF)
     */
    static String formatCommand(String action, String path) {
        return switch (action.toLowerCase()) {
            case "list" -> path.isBlank() ? "LIST" : "LIST " + path;
            case "download" -> "RETR " + path;
            case "upload" -> "STOR " + path;
            default -> throw new IllegalArgumentException("Unknown FTP action: " + action);
        };
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
