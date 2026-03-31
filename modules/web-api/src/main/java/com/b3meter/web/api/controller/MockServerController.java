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
package com.b3meter.web.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * REST controller for mock server lifecycle and self-smoke testing.
 * Ports are configurable via application.yml (b3meter.mocks.*) to avoid
 * conflicts with other services running on the same machine.
 */
@RestController
@RequestMapping("/api/v1/mocks")
@Tag(name = "Mock Servers", description = "Start/stop protocol mock servers and run smoke tests")
public class MockServerController {

    private final ConcurrentHashMap<String, Process> mockProcesses = new ConcurrentHashMap<>();
    private static final File PROJECT_ROOT = findProjectRoot();

    @Value("${b3meter.mocks.http-port:9081}")      private int httpPort;
    @Value("${b3meter.mocks.ws-port:9082}")         private int wsPort;
    @Value("${b3meter.mocks.sse-port:9083}")        private int ssePort;
    @Value("${b3meter.mocks.hls-port:9084}")        private int hlsPort;
    @Value("${b3meter.mocks.mqtt-port:9883}")       private int mqttPort;
    @Value("${b3meter.mocks.mqtt-health-port:9884}") private int mqttHealthPort;
    @Value("${b3meter.mocks.grpc-port:9051}")       private int grpcPort;
    @Value("${b3meter.mocks.grpc-health-port:9085}") private int grpcHealthPort;
    @Value("${b3meter.mocks.dash-port:9086}")       private int dashPort;
    @Value("${b3meter.mocks.stun-port:9087}")       private int stunPort;
    @Value("${b3meter.mocks.webrtc-port:9088}")     private int webrtcPort;
    @Value("${b3meter.mocks.ftp-port:9122}")        private int ftpPort;
    @Value("${b3meter.mocks.ftp-health-port:9123}")  private int ftpHealthPort;
    @Value("${b3meter.mocks.ldap-port:9390}")       private int ldapPort;
    @Value("${b3meter.mocks.ldap-health-port:9391}") private int ldapHealthPort;
    @Value("${b3meter.mocks.tcp-port:9089}")         private int tcpPort;
    @Value("${b3meter.mocks.tcp-health-port:9090}")  private int tcpHealthPort;
    @Value("${b3meter.mocks.smtp-port:9025}")        private int smtpPort;
    @Value("${b3meter.mocks.smtp-health-port:9026}") private int smtpHealthPort;

    private List<MockServerDef> servers;

    @PostConstruct
    void initServers() {
        servers = List.of(
            new MockServerDef("http-mock",        httpPort,  "HTTP",   "test-servers/http-mock",             "server.js"),
            new MockServerDef("ws-mock",          wsPort,    "WS",     "test-servers/ws-mock",               "server.js"),
            new MockServerDef("sse-mock",         ssePort,   "SSE",    "test-servers/sse-mock",              "server.js"),
            new MockServerDef("hls-mock",         hlsPort,   "HLS",    "test-servers/hls-mock",              "server.js"),
            new MockServerDef("mqtt-mock",        mqttPort,  "MQTT",   "test-servers/mqtt-mock",             "broker.js", mqttHealthPort),
            new MockServerDef("grpc-mock",        grpcPort,  "gRPC",   "test-servers/grpc-mock",             "server.js", grpcHealthPort),
            new MockServerDef("dash-mock",        dashPort,  "DASH",   "test-servers/dash-mock",             "server.js"),
            new MockServerDef("stun-mock",        stunPort,  "STUN",   "test-servers/stun-mock",             "server.js"),
            new MockServerDef("webrtc-signaling", webrtcPort,"WebRTC", "test-servers/webrtc-signaling-mock", "server.js"),
            new MockServerDef("ftp-mock",         ftpPort,   "FTP",    "test-servers/ftp-mock",              "server.js", ftpHealthPort),
            new MockServerDef("ldap-mock",        ldapPort,  "LDAP",   "test-servers/ldap-mock",             "server.js", ldapHealthPort),
            new MockServerDef("tcp-mock",         tcpPort,   "TCP",    "test-servers/tcp-mock",              "server.js", tcpHealthPort),
            new MockServerDef("smtp-mock",        smtpPort,  "SMTP",   "test-servers/smtp-mock",             "server.js", smtpHealthPort)
        );
    }

    private static File findProjectRoot() {
        File cwd = new File(System.getProperty("user.dir"));
        if (new File(cwd, "test-servers").isDirectory()) return cwd;
        File parent = cwd.getParentFile();
        if (parent != null && new File(parent, "test-servers").isDirectory()) return parent;
        File grandparent = parent != null ? parent.getParentFile() : null;
        if (grandparent != null && new File(grandparent, "test-servers").isDirectory()) return grandparent;
        return cwd;
    }

    private static final List<String> SMOKE_PLANS = List.of(
            "http-smoke", "ws-smoke", "sse-smoke", "hls-smoke",
            "mqtt-smoke", "grpc-smoke", "dash-smoke", "stun-smoke",
            "webrtc-smoke", "ftp-smoke", "ldap-smoke",
            "tcp-smoke", "smtp-smoke"
    );

    @Operation(summary = "Start all mock servers", description = "Launches all 13 protocol mock servers as background processes")
    @ApiResponse(responseCode = "200", description = "Servers started (with counts and any errors)")
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAll() {
        List<String> started = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MockServerDef def : servers) {
            if (mockProcesses.containsKey(def.name) && mockProcesses.get(def.name).isAlive()) {
                continue;
            }
            try {
                String nodePath = resolveNodePath();
                File serverDir = new File(PROJECT_ROOT, def.directory);
                if (!serverDir.isDirectory()) {
                    errors.add(def.name + ": directory not found: " + serverDir.getAbsolutePath());
                    continue;
                }

                File packageJson = new File(serverDir, "package.json");
                File nodeModules = new File(serverDir, "node_modules");
                if (packageJson.exists() && !nodeModules.exists()) {
                    try {
                        String npmPath = resolveNpmPath();
                        ProcessBuilder npmInstall = new ProcessBuilder(npmPath, "install", "--silent");
                        npmInstall.directory(serverDir);
                        npmInstall.redirectErrorStream(true);
                        Process npm = npmInstall.start();
                        boolean finished = npm.waitFor(30, TimeUnit.SECONDS);
                        if (!finished) {
                            npm.destroyForcibly();
                            errors.add(def.name + ": npm install timed out");
                            continue;
                        }
                        if (npm.exitValue() != 0) {
                            byte[] output = npm.getInputStream().readAllBytes();
                            errors.add(def.name + ": npm install failed: " + new String(output).trim());
                            continue;
                        }
                    } catch (Exception npmEx) {
                        errors.add(def.name + ": npm install error: " + npmEx.getMessage());
                        continue;
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(nodePath, def.scriptFile);
                pb.directory(serverDir);
                pb.redirectErrorStream(true);
                pb.environment().put("PORT", String.valueOf(def.port));
                if (def.healthPort != def.port) {
                    pb.environment().put("HEALTH_PORT", String.valueOf(def.healthPort));
                }
                Process process = pb.start();
                mockProcesses.put(def.name, process);
                started.add(def.name);
            } catch (Exception e) {
                errors.add(def.name + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("started", started.size());
        result.put("servers", started);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Stop all mock servers", description = "Kills all running mock server processes")
    @ApiResponse(responseCode = "200", description = "Servers stopped")
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopAll() {
        int stopped = 0;

        for (Map.Entry<String, Process> entry : mockProcesses.entrySet()) {
            Process p = entry.getValue();
            if (p.isAlive()) {
                p.destroyForcibly();
                stopped++;
            }
        }
        mockProcesses.clear();

        for (MockServerDef def : servers) {
            int[] ports = (def.healthPort != def.port)
                    ? new int[]{def.port, def.healthPort}
                    : new int[]{def.port};
            for (int port : ports) {
                try {
                    ProcessBuilder lsof = new ProcessBuilder("lsof", "-ti", ":" + port);
                    lsof.redirectErrorStream(true);
                    Process p = lsof.start();
                    String pids = new String(p.getInputStream().readAllBytes()).trim();
                    p.waitFor(5, TimeUnit.SECONDS);
                    if (!pids.isBlank()) {
                        for (String pid : pids.split("\\s+")) {
                            try {
                                new ProcessBuilder("kill", "-9", pid.trim()).start().waitFor(3, TimeUnit.SECONDS);
                                stopped++;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return ResponseEntity.ok(Map.of("stopped", stopped));
    }

    @Operation(summary = "Mock server status", description = "Health-checks all mock servers and returns their status")
    @ApiResponse(responseCode = "200", description = "Status map returned")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (MockServerDef def : servers) {
            Map<String, Object> serverStatus = new LinkedHashMap<>();
            serverStatus.put("port", def.port);
            serverStatus.put("protocol", def.protocol);

            long start = System.currentTimeMillis();
            try {
                URL url = URI.create("http://localhost:" + def.healthPort + "/health").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                long elapsed = System.currentTimeMillis() - start;
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    serverStatus.put("status", "up");
                    serverStatus.put("responseTime", elapsed);
                } else {
                    serverStatus.put("status", "down");
                    serverStatus.put("responseTime", elapsed);
                    serverStatus.put("httpCode", code);
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                serverStatus.put("status", "down");
                serverStatus.put("responseTime", elapsed);
                serverStatus.put("error", e.getClass().getSimpleName());
            }

            result.put(def.name, serverStatus);
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Run smoke tests", description = "Executes all 13 smoke test plans sequentially against running mock servers")
    @ApiResponse(responseCode = "200", description = "Smoke test results with per-plan and overall summary")
    @PostMapping("/smoke")
    public ResponseEntity<Map<String, Object>> runSmoke() {
        List<Map<String, Object>> results = new ArrayList<>();
        int totalSamples = 0;
        int totalErrors = 0;
        int passed = 0;
        int skipped = 0;

        for (String planName : SMOKE_PLANS) {
            Map<String, Object> planResult = new LinkedHashMap<>();
            planResult.put("plan", planName);

            String serverName = planName.replace("-smoke", "-mock");
            MockServerDef serverDef = servers.stream()
                    .filter(s -> s.name.equals(serverName) ||
                            (planName.equals("webrtc-smoke") && s.name.equals("webrtc-signaling")))
                    .findFirst()
                    .orElse(null);

            boolean serverUp = false;
            if (serverDef != null) {
                try {
                    URL url = URI.create("http://localhost:" + serverDef.healthPort + "/health").toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    serverUp = code >= 200 && code < 300;
                } catch (Exception ignored) {}
            }

            if (!serverUp) {
                planResult.put("status", "SKIP");
                planResult.put("reason", "Server down");
                planResult.put("samples", 0);
                planResult.put("avgResponseTime", 0);
                planResult.put("errorPercent", 0.0);
                planResult.put("p95", 0);
                skipped++;
                results.add(planResult);
                continue;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "java", "-jar", "build/libs/b3meter.jar",
                        "--plan", "smoke-plans/" + planName + ".jmx",
                        "--non-gui", "--duration", "10"
                );
                pb.redirectErrorStream(true);
                pb.directory(new File("."));
                Process process = pb.start();

                byte[] output = process.getInputStream().readAllBytes();
                int exitCode = process.waitFor();

                String stdout = new String(output);
                int samples = parseSamples(stdout);
                double errorPct = parseErrorPercent(stdout);
                long avgRt = parseAvgResponseTime(stdout);
                long p95 = parseP95(stdout);

                planResult.put("samples", samples);
                planResult.put("avgResponseTime", avgRt);
                planResult.put("errorPercent", errorPct);
                planResult.put("p95", p95);
                totalSamples += samples;
                totalErrors += (int) Math.round(samples * errorPct / 100.0);

                if (errorPct < 0.01) {
                    planResult.put("status", "PASS");
                    passed++;
                } else if (errorPct < 5.0) {
                    planResult.put("status", "WARN");
                    passed++;
                } else {
                    planResult.put("status", "FAIL");
                }
            } catch (Exception e) {
                planResult.put("status", "ERROR");
                planResult.put("error", e.getMessage());
                planResult.put("samples", 0);
                planResult.put("avgResponseTime", 0);
                planResult.put("errorPercent", 100.0);
                planResult.put("p95", 0);
            }

            results.add(planResult);
        }

        double overallErrorPct = totalSamples > 0
                ? (totalErrors * 100.0 / totalSamples) : 0.0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        response.put("summary", Map.of(
                "total", SMOKE_PLANS.size(),
                "passed", passed,
                "skipped", skipped,
                "failed", SMOKE_PLANS.size() - passed - skipped,
                "totalSamples", totalSamples,
                "overallErrorPercent", Math.round(overallErrorPct * 100.0) / 100.0
        ));

        return ResponseEntity.ok(response);
    }

    private int parseSamples(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:samples|sampleCount)\\s*[=:]\\s*(\\d+)")
                .matcher(output);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private double parseErrorPercent(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:errorPercent|error%)\\s*[=:]\\s*([\\d.]+)")
                .matcher(output);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private long parseAvgResponseTime(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:avgResponseTime|avg)\\s*[=:]\\s*(\\d+)")
                .matcher(output);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    private long parseP95(String output) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:p95|percentile95)\\s*[=:]\\s*(\\d+)")
                .matcher(output);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    private record MockServerDef(String name, int port, String protocol, String directory, String scriptFile, int healthPort) {
        MockServerDef(String name, int port, String protocol, String directory, String scriptFile) {
            this(name, port, protocol, directory, scriptFile, port);
        }
    }

    private static String resolveNodePath() {
        String[] candidates = {
                "/opt/homebrew/bin/node",
                "/usr/local/bin/node",
                "/usr/bin/node",
                "node"
        };
        for (String path : candidates) {
            if (new File(path).canExecute()) return path;
        }
        return "node";
    }

    private static String resolveNpmPath() {
        String[] candidates = {
                "/opt/homebrew/bin/npm",
                "/usr/local/bin/npm",
                "/usr/bin/npm",
                "npm"
        };
        for (String path : candidates) {
            if (new File(path).canExecute()) return path;
        }
        return "npm";
    }
}
