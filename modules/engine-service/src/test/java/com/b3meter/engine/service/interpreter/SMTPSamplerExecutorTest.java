/*
 * Copyright 2024-2026 b3meter Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b3meter.engine.service.interpreter;

import com.b3meter.engine.service.plan.PlanNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SMTPSamplerExecutor} using an in-test minimal SMTP
 * {@link ServerSocket}.
 *
 * <p>Each test starts an in-process SMTP state machine (banner → EHLO → MAIL →
 * RCPT → DATA → message dot → QUIT → 221 bye). No external Node.js server is
 * required.
 */
class SMTPSamplerExecutorTest {

    private ServerSocket serverSocket;
    private int serverPort;
    private ExecutorService acceptThread;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0);
        serverPort = serverSocket.getLocalPort();
        acceptThread = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (acceptThread != null) {
            acceptThread.shutdownNow();
        }
    }

    // =========================================================================
    // Test 1: Full successful SMTP session
    // =========================================================================

    @Test
    void successfulSmtpSession() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);

        acceptThread.submit(() -> {
            ready.countDown();
            try (Socket client = serverSocket.accept()) {
                runSmtpStateMachine(client, true /* wellBehaved */);
            } catch (IOException ignored) {
            }
        });

        ready.await(2, TimeUnit.SECONDS);

        PlanNode node = smtpNode("localhost", serverPort,
                "sender@test.local", "rcpt@test.local",
                "Unit Test", "hello smtp", 3000);
        SampleResult result = new SampleResult("smtp-ok");
        Map<String, String> vars = new HashMap<>();

        SMTPSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(),
                "Expected SMTP session to succeed; error: " + result.getFailureMessage());
        assertEquals(250, result.getStatusCode());
    }

    // =========================================================================
    // Test 2: Connection to dead port returns clean failure
    // =========================================================================

    @Test
    void connectionFailureReturnsCleanFailure() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }

        PlanNode node = smtpNode("localhost", serverPort,
                "a@b.com", "c@d.com", "subj", "body", 1000);
        SampleResult result = new SampleResult("smtp-refused");
        Map<String, String> vars = new HashMap<>();

        assertDoesNotThrow(() -> SMTPSamplerExecutor.execute(node, result, vars));

        assertFalse(result.isSuccess(), "Expected failure when server not available");
        assertFalse(result.getFailureMessage().isBlank(),
                "Failure message must not be blank");
    }

    // =========================================================================
    // Test 3: Malformed server banner returns informative failure
    // =========================================================================

    @Test
    void malformedServerResponseHandled() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);

        acceptThread.submit(() -> {
            ready.countDown();
            try (Socket client = serverSocket.accept()) {
                OutputStream out = client.getOutputStream();
                // Send garbage instead of "220 banner"
                out.write("GARBAGE NOT SMTP\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                // Keep connection open briefly so executor can read it
                Thread.sleep(500);
            } catch (IOException | InterruptedException ignored) {
            }
        });

        ready.await(2, TimeUnit.SECONDS);

        PlanNode node = smtpNode("localhost", serverPort,
                "a@b.com", "c@d.com", "subj", "body", 3000);
        SampleResult result = new SampleResult("smtp-garbage");
        Map<String, String> vars = new HashMap<>();

        assertDoesNotThrow(() -> SMTPSamplerExecutor.execute(node, result, vars));

        assertFalse(result.isSuccess(),
                "Malformed banner should result in failure");
        assertFalse(result.getFailureMessage().isBlank(),
                "Failure message should be informative");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Minimal in-process SMTP state machine:
     * sends correct RFC 5321 reply codes for each command.
     */
    private static void runSmtpStateMachine(Socket client, boolean wellBehaved) throws IOException {
        OutputStream out = client.getOutputStream();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

        smtp(out, "220 localhost b3meter-test SMTP\r\n");

        String line;
        boolean inData = false;
        while ((line = reader.readLine()) != null) {
            String upper = line.trim().toUpperCase();
            if (inData) {
                if (line.equals(".")) {
                    smtp(out, "250 OK message accepted\r\n");
                    inData = false;
                }
                // else: consuming message body lines
            } else if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                smtp(out, "250-localhost Hello\r\n");
                smtp(out, "250 OK\r\n");
            } else if (upper.startsWith("MAIL FROM")) {
                smtp(out, "250 OK\r\n");
            } else if (upper.startsWith("RCPT TO")) {
                smtp(out, "250 OK\r\n");
            } else if (upper.equals("DATA")) {
                smtp(out, "354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                inData = true;
            } else if (upper.startsWith("QUIT")) {
                smtp(out, "221 Bye\r\n");
                break;
            } else {
                smtp(out, "500 Unknown command\r\n");
            }
        }
    }

    private static void smtp(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static PlanNode smtpNode(String host, int port,
                                      String from, String to,
                                      String subject, String body,
                                      int timeoutMs) {
        return PlanNode.builder("SmtpSampler", "smtp-test")
                .property("SmtpSampler.host", host)
                .property("SmtpSampler.port", port)
                .property("SmtpSampler.from", from)
                .property("SmtpSampler.to", to)
                .property("SmtpSampler.subject", subject)
                .property("SmtpSampler.body", body)
                .property("SmtpSampler.timeout", timeoutMs)
                .build();
    }
}
