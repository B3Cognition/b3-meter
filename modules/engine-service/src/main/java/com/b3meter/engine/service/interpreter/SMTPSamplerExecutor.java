package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes an {@code SmtpSampler} {@link PlanNode} using raw TCP sockets
 * implementing the SMTP text protocol.
 *
 * <p>Since jakarta.mail cannot be added (no external dependencies — Constitution
 * Principle I), this executor implements a simple SMTP conversation over raw sockets:
 * <ol>
 *   <li>Connect to {@code smtpHost:smtpPort} (default 25)</li>
 *   <li>Read server greeting (220)</li>
 *   <li>Send {@code EHLO hostname}, read response</li>
 *   <li>Send {@code MAIL FROM:<sender>}, read response</li>
 *   <li>Send {@code RCPT TO:<recipient>}, read response</li>
 *   <li>Send {@code DATA}, read response</li>
 *   <li>Send {@code Subject: {subject}\r\n\r\n{body}\r\n.\r\n}, read response</li>
 *   <li>Send {@code QUIT}</li>
 *   <li>Set SampleResult based on response codes</li>
 * </ol>
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code SmtpSampler.host} — SMTP server hostname</li>
 *   <li>{@code SmtpSampler.port} — SMTP port (default 25)</li>
 *   <li>{@code SmtpSampler.from} — sender email address</li>
 *   <li>{@code SmtpSampler.to} — recipient email address</li>
 *   <li>{@code SmtpSampler.subject} — email subject line</li>
 *   <li>{@code SmtpSampler.body} — email body text</li>
 *   <li>{@code SmtpSampler.timeout} — connection timeout in ms (default 10000)</li>
 * </ul>
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class SMTPSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(SMTPSamplerExecutor.class.getName());

    private static final int DEFAULT_PORT = 25;
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private SMTPSamplerExecutor() {
        // utility class — not instantiable
    }

    /**
     * Executes the SMTP operation described by {@code node}.
     *
     * @param node      the SmtpSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope for {@code ${varName}} substitution
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String host = resolve(node.getStringProp("SmtpSampler.host", ""), variables);
        int port = node.getIntProp("SmtpSampler.port", DEFAULT_PORT);
        String from = resolve(node.getStringProp("SmtpSampler.from", ""), variables);
        String to = resolve(node.getStringProp("SmtpSampler.to", ""), variables);
        String subject = resolve(node.getStringProp("SmtpSampler.subject", ""), variables);
        String body = resolve(node.getStringProp("SmtpSampler.body", ""), variables);
        int timeout = node.getIntProp("SmtpSampler.timeout", DEFAULT_TIMEOUT_MS);

        if (host.isBlank()) {
            result.setFailureMessage("SmtpSampler.host is empty");
            return;
        }

        if (from.isBlank()) {
            result.setFailureMessage("SmtpSampler.from is empty");
            return;
        }

        if (to.isBlank()) {
            result.setFailureMessage("SmtpSampler.to is empty");
            return;
        }

        LOG.log(Level.FINE, "SMTPSamplerExecutor: sending to {0}:{1} from={2} to={3}",
                new Object[]{host, port, from, to});

        long start = System.currentTimeMillis();
        StringBuilder transcript = new StringBuilder();

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(host, port), timeout);

            long connectTime = System.currentTimeMillis() - start;
            result.setConnectTimeMs(connectTime);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();

            // 1. Read server greeting (expect 220)
            String greeting = readLine(reader);
            transcript.append("S: ").append(greeting).append('\n');
            int greetingCode = parseCode(greeting);
            if (greetingCode != 220) {
                result.setStatusCode(greetingCode);
                result.setFailureMessage("SMTP unexpected greeting: " + greeting);
                result.setResponseBody(transcript.toString());
                return;
            }

            result.setLatencyMs(System.currentTimeMillis() - start - connectTime);

            // 2. EHLO
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            sendLine(out, "EHLO " + hostname, transcript);
            String ehloReply = readMultiLineReply(reader, transcript);
            int ehloCode = parseCode(ehloReply);
            if (ehloCode != 250) {
                result.setStatusCode(ehloCode);
                result.setFailureMessage("SMTP EHLO failed: " + ehloReply);
                result.setResponseBody(transcript.toString());
                return;
            }

            // 3. MAIL FROM
            sendLine(out, "MAIL FROM:<" + from + ">", transcript);
            String mailReply = readLine(reader);
            transcript.append("S: ").append(mailReply).append('\n');
            int mailCode = parseCode(mailReply);
            if (mailCode != 250) {
                result.setStatusCode(mailCode);
                result.setFailureMessage("SMTP MAIL FROM failed: " + mailReply);
                result.setResponseBody(transcript.toString());
                return;
            }

            // 4. RCPT TO
            sendLine(out, "RCPT TO:<" + to + ">", transcript);
            String rcptReply = readLine(reader);
            transcript.append("S: ").append(rcptReply).append('\n');
            int rcptCode = parseCode(rcptReply);
            if (rcptCode != 250) {
                result.setStatusCode(rcptCode);
                result.setFailureMessage("SMTP RCPT TO failed: " + rcptReply);
                result.setResponseBody(transcript.toString());
                return;
            }

            // 5. DATA
            sendLine(out, "DATA", transcript);
            String dataReply = readLine(reader);
            transcript.append("S: ").append(dataReply).append('\n');
            int dataCode = parseCode(dataReply);
            if (dataCode != 354) {
                result.setStatusCode(dataCode);
                result.setFailureMessage("SMTP DATA failed: " + dataReply);
                result.setResponseBody(transcript.toString());
                return;
            }

            // 6. Send message content (Subject + body + terminating dot)
            String messageContent = "Subject: " + subject + "\r\n\r\n" + body + "\r\n.\r\n";
            out.write(messageContent.getBytes(StandardCharsets.UTF_8));
            out.flush();
            transcript.append("C: [message data: ").append(body.length()).append(" bytes]\n");

            String dotReply = readLine(reader);
            transcript.append("S: ").append(dotReply).append('\n');
            int dotCode = parseCode(dotReply);

            // 7. QUIT
            sendLine(out, "QUIT", transcript);
            try {
                String quitReply = readLine(reader);
                transcript.append("S: ").append(quitReply).append('\n');
            } catch (IOException ignored) {
                // server may close immediately after QUIT
            }

            // Set result
            result.setStatusCode(dotCode);
            result.setResponseBody(transcript.toString());

            if (dotCode != 250) {
                result.setFailureMessage("SMTP message not accepted: " + dotReply);
            }

            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);

        } catch (IOException e) {
            long total = System.currentTimeMillis() - start;
            result.setTotalTimeMs(total);
            result.setResponseBody(transcript.toString());
            result.setFailureMessage("SMTP error: " + e.getMessage());
            LOG.log(Level.WARNING, "SMTPSamplerExecutor: error for " + host + ":" + port, e);
        }
    }

    // -------------------------------------------------------------------------
    // SMTP protocol helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a command line (appends CRLF) and records it in the transcript.
     */
    private static void sendLine(OutputStream out, String command,
                                  StringBuilder transcript) throws IOException {
        out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        transcript.append("C: ").append(command).append('\n');
    }

    /**
     * Reads a single line from the SMTP server.
     */
    private static String readLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("SMTP connection closed unexpectedly");
        }
        return line;
    }

    /**
     * Reads a multi-line SMTP reply (e.g., EHLO response with continuation lines).
     * Lines ending with {@code NNN-} continue; {@code NNN } ends the reply.
     *
     * @return the last line of the reply (containing the final status code)
     */
    private static String readMultiLineReply(BufferedReader reader,
                                              StringBuilder transcript) throws IOException {
        String line;
        String lastLine = null;
        while (true) {
            line = readLine(reader);
            transcript.append("S: ").append(line).append('\n');
            lastLine = line;
            // Check if this is a continuation line (NNN-...)
            if (line.length() >= 4
                    && Character.isDigit(line.charAt(0))
                    && Character.isDigit(line.charAt(1))
                    && Character.isDigit(line.charAt(2))
                    && line.charAt(3) == '-') {
                continue; // more lines coming
            }
            break; // final line (NNN space) or non-standard
        }
        return lastLine;
    }

    /**
     * Parses the 3-digit reply code from an SMTP response line.
     *
     * @param reply the reply string (e.g., "250 OK")
     * @return the numeric reply code, or -1 if unparseable
     */
    static int parseCode(String reply) {
        if (reply == null || reply.length() < 3) return -1;
        try {
            return Integer.parseInt(reply.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
