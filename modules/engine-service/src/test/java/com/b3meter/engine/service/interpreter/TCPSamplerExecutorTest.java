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

import java.io.IOException;
import java.io.InputStream;
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
 * Unit tests for {@link TCPSamplerExecutor} using an in-test {@link ServerSocket}.
 *
 * <p>Each test gets a fresh server socket on a dynamically assigned port.
 * No external TCP mock server is required.
 */
class TCPSamplerExecutorTest {

    private ServerSocket serverSocket;
    private int serverPort;
    private ExecutorService acceptThread;

    @BeforeEach
    void startEchoServer() throws IOException {
        serverSocket = new ServerSocket(0); // bind to any free port
        serverPort = serverSocket.getLocalPort();
        acceptThread = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopEchoServer() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (acceptThread != null) {
            acceptThread.shutdownNow();
        }
    }

    // =========================================================================
    // Test 1: Successful echo exchange
    // =========================================================================

    @Test
    void successfulEchoExchange() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);

        acceptThread.submit(() -> {
            ready.countDown();
            try (Socket client = serverSocket.accept()) {
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();

                byte[] buf = new byte[256];
                int n = in.read(buf);
                if (n > 0) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            } catch (IOException ignored) {
                // server closed or client disconnected
            }
        });

        ready.await(2, TimeUnit.SECONDS);

        PlanNode node = tcpNode("localhost", serverPort, "hello\n", 2000);
        SampleResult result = new SampleResult("tcp-echo");
        Map<String, String> vars = new HashMap<>();

        TCPSamplerExecutor.execute(node, result, vars);

        assertTrue(result.isSuccess(),
                "Expected success=true; error: " + result.getFailureMessage());
        assertTrue(result.getResponseBody().contains("hello"),
                "Response should echo back 'hello': " + result.getResponseBody());
        assertEquals(0, result.getStatusCode());
    }

    // =========================================================================
    // Test 2: Connection refused returns clean failure
    // =========================================================================

    @Test
    void connectionRefusedReturnsCleanFailure() {
        // Close the server so nothing is listening
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }

        PlanNode node = tcpNode("localhost", serverPort, "hello\n", 1000);
        SampleResult result = new SampleResult("tcp-refused");
        Map<String, String> vars = new HashMap<>();

        // Must not throw — executor catches all IOExceptions
        assertDoesNotThrow(() -> TCPSamplerExecutor.execute(node, result, vars));

        assertFalse(result.isSuccess(), "Expected success=false when connection refused");
        assertFalse(result.getFailureMessage().isBlank(),
                "Failure message should not be blank");
    }

    // =========================================================================
    // Test 3: Server closes connection without sending data
    // =========================================================================

    @Test
    void emptyResponseHandled() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);

        acceptThread.submit(() -> {
            ready.countDown();
            try (Socket client = serverSocket.accept()) {
                // Drain input and close immediately without writing anything
                client.getInputStream().read(new byte[256]);
            } catch (IOException ignored) {
            }
        });

        ready.await(2, TimeUnit.SECONDS);

        PlanNode node = tcpNode("localhost", serverPort, "hello\n", 2000);
        SampleResult result = new SampleResult("tcp-empty");
        Map<String, String> vars = new HashMap<>();

        assertDoesNotThrow(() -> TCPSamplerExecutor.execute(node, result, vars));

        // Either success with empty body or failure — must not NPE
        String body = result.getResponseBody();
        assertNotNull(body, "Response body must never be null");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode tcpNode(String host, int port, String payload, int timeoutMs) {
        return PlanNode.builder("TCPSampler", "tcp-test")
                .property("TCPSampler.host", host)
                .property("TCPSampler.port", port)
                .property("TCPSampler.payload", payload)
                .property("TCPSampler.timeout", timeoutMs)
                .property("TCPSampler.responseSize", 256)
                .build();
    }
}
