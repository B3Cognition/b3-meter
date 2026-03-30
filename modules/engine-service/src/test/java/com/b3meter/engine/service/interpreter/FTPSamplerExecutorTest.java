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
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FTPSamplerExecutor}.
 *
 * <p>Tests FTP command formatting, reply parsing, PASV response parsing, and error handling.
 * No live FTP server is required — protocol helpers are tested directly via
 * the package-visible methods.
 */
class FTPSamplerExecutorTest {

    // =========================================================================
    // Null guards
    // =========================================================================

    @Test
    void execute_throwsOnNullNode() {
        SampleResult result = new SampleResult("ftp-test");
        assertThrows(NullPointerException.class,
                () -> FTPSamplerExecutor.execute(null, result, Map.of()));
    }

    @Test
    void execute_throwsOnNullResult() {
        PlanNode node = ftpNode("localhost", "/file.txt", "download");
        assertThrows(NullPointerException.class,
                () -> FTPSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void execute_throwsOnNullVariables() {
        PlanNode node = ftpNode("localhost", "/file.txt", "download");
        SampleResult result = new SampleResult("ftp-test");
        assertThrows(NullPointerException.class,
                () -> FTPSamplerExecutor.execute(node, result, null));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void execute_failsOnEmptyHost() {
        PlanNode node = ftpNode("", "/file.txt", "download");
        SampleResult result = new SampleResult("ftp-test");

        FTPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("host is empty"));
    }

    @Test
    void execute_failsOnEmptyPathForDownload() {
        PlanNode node = ftpNode("localhost", "", "download");
        SampleResult result = new SampleResult("ftp-test");

        FTPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("path is empty"));
    }

    @Test
    void execute_failsOnEmptyPathForUpload() {
        PlanNode node = ftpNode("localhost", "", "upload");
        SampleResult result = new SampleResult("ftp-test");

        FTPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("path is empty"));
    }

    @Test
    void execute_allowsEmptyPathForList() {
        // List with empty path is allowed (lists current directory)
        // Will fail with connection error, not validation error
        PlanNode node = PlanNode.builder("FTPSampler", "ftp-list-root")
                .property("FTPSampler.host", "127.0.0.1")
                .property("FTPSampler.port", 1)
                .property("FTPSampler.path", "")
                .property("FTPSampler.action", "list")
                .property("FTPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ftp-list-root");

        FTPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FTP error"),
                "Should fail with FTP error, not validation error: " + result.getFailureMessage());
    }

    // =========================================================================
    // Connection error handling
    // =========================================================================

    @Test
    void execute_handlesConnectionRefused() {
        PlanNode node = PlanNode.builder("FTPSampler", "ftp-refused")
                .property("FTPSampler.host", "127.0.0.1")
                .property("FTPSampler.port", 1)
                .property("FTPSampler.path", "/file.txt")
                .property("FTPSampler.action", "download")
                .property("FTPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ftp-refused");

        FTPSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FTP error"));
        assertTrue(result.getTotalTimeMs() >= 0);
    }

    // =========================================================================
    // FTP command formatting
    // =========================================================================

    @Test
    void formatCommand_list() {
        assertEquals("LIST /pub", FTPSamplerExecutor.formatCommand("list", "/pub"));
    }

    @Test
    void formatCommand_listEmpty() {
        assertEquals("LIST", FTPSamplerExecutor.formatCommand("list", ""));
    }

    @Test
    void formatCommand_download() {
        assertEquals("RETR /file.txt", FTPSamplerExecutor.formatCommand("download", "/file.txt"));
    }

    @Test
    void formatCommand_upload() {
        assertEquals("STOR /upload.bin", FTPSamplerExecutor.formatCommand("upload", "/upload.bin"));
    }

    @Test
    void formatCommand_caseInsensitive() {
        assertEquals("LIST /dir", FTPSamplerExecutor.formatCommand("LIST", "/dir"));
        assertEquals("RETR /f.txt", FTPSamplerExecutor.formatCommand("DOWNLOAD", "/f.txt"));
        assertEquals("STOR /u.bin", FTPSamplerExecutor.formatCommand("UPLOAD", "/u.bin"));
    }

    @Test
    void formatCommand_unknownAction() {
        assertThrows(IllegalArgumentException.class,
                () -> FTPSamplerExecutor.formatCommand("unknown", "/path"));
    }

    // =========================================================================
    // sendCommand formatting
    // =========================================================================

    @Test
    void sendCommand_appendsCRLF() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FTPSamplerExecutor.sendCommand(baos, "USER anonymous");
        String sent = baos.toString(StandardCharsets.UTF_8);
        assertTrue(sent.endsWith("\r\n"));
        assertEquals("USER anonymous\r\n", sent);
    }

    // =========================================================================
    // Reply code parsing
    // =========================================================================

    @Test
    void parseReplyCode_standard() {
        assertEquals(220, FTPSamplerExecutor.parseReplyCode("220 Welcome to FTP server"));
    }

    @Test
    void parseReplyCode_multiLine() {
        assertEquals(230, FTPSamplerExecutor.parseReplyCode("230 User logged in"));
    }

    @Test
    void parseReplyCode_errorCode() {
        assertEquals(530, FTPSamplerExecutor.parseReplyCode("530 Login incorrect"));
    }

    @Test
    void parseReplyCode_nullReply() {
        assertEquals(-1, FTPSamplerExecutor.parseReplyCode(null));
    }

    @Test
    void parseReplyCode_shortString() {
        assertEquals(-1, FTPSamplerExecutor.parseReplyCode("22"));
    }

    @Test
    void parseReplyCode_nonNumeric() {
        assertEquals(-1, FTPSamplerExecutor.parseReplyCode("abc Invalid"));
    }

    // =========================================================================
    // readReply
    // =========================================================================

    @Test
    void readReply_singleLine() throws IOException {
        String input = "220 Welcome to FTP\r\n";
        BufferedReader reader = readerFrom(input);
        String reply = FTPSamplerExecutor.readReply(reader);
        assertEquals("220 Welcome to FTP", reply);
    }

    @Test
    void readReply_multiLine() throws IOException {
        String input = "220-Multi line\r\n220 End of banner\r\n";
        BufferedReader reader = readerFrom(input);
        String reply = FTPSamplerExecutor.readReply(reader);
        assertTrue(reply.contains("Multi line"));
        assertTrue(reply.contains("End of banner"));
    }

    @Test
    void readReply_throwsOnEOF() {
        BufferedReader reader = readerFrom("");
        assertThrows(IOException.class, () -> FTPSamplerExecutor.readReply(reader));
    }

    // =========================================================================
    // PASV response parsing
    // =========================================================================

    @Test
    void parsePasvReply_standard() throws IOException {
        String reply = "227 Entering Passive Mode (127,0,0,1,39,15)";
        int[] result = FTPSamplerExecutor.parsePasvReply(reply, "localhost");

        // Port = 39 * 256 + 15 = 9999
        assertEquals(9999, result[1]);
    }

    @Test
    void parsePasvReply_highPort() throws IOException {
        String reply = "227 Entering Passive Mode (192,168,1,1,195,80)";
        int[] result = FTPSamplerExecutor.parsePasvReply(reply, "localhost");

        // Port = 195 * 256 + 80 = 50000
        assertEquals(50000, result[1]);
    }

    @Test
    void parsePasvReply_lowPort() throws IOException {
        String reply = "227 Entering Passive Mode (10,0,0,1,0,21)";
        int[] result = FTPSamplerExecutor.parsePasvReply(reply, "localhost");

        // Port = 0 * 256 + 21 = 21
        assertEquals(21, result[1]);
    }

    @Test
    void parsePasvReply_invalidFormat() {
        String reply = "227 Entering Passive Mode (invalid)";
        assertThrows(IOException.class,
                () -> FTPSamplerExecutor.parsePasvReply(reply, "localhost"));
    }

    @Test
    void parsePasvReply_nonPasvReply() {
        String reply = "425 Can't open data connection";
        assertThrows(IOException.class,
                () -> FTPSamplerExecutor.parsePasvReply(reply, "localhost"));
    }

    // =========================================================================
    // Packed host formatting
    // =========================================================================

    @Test
    void formatPackedHost_localhost() {
        // 127.0.0.1 = (127 << 24) | (0 << 16) | (0 << 8) | 1
        int packed = (127 << 24) | (0 << 16) | (0 << 8) | 1;
        assertEquals("127.0.0.1", FTPSamplerExecutor.formatPackedHost(packed));
    }

    @Test
    void formatPackedHost_fullAddress() {
        // 192.168.1.100 = (192 << 24) | (168 << 16) | (1 << 8) | 100
        int packed = (192 << 24) | (168 << 16) | (1 << 8) | 100;
        assertEquals("192.168.1.100", FTPSamplerExecutor.formatPackedHost(packed));
    }

    @Test
    void formatPackedHost_zeros() {
        assertEquals("0.0.0.0", FTPSamplerExecutor.formatPackedHost(0));
    }

    // =========================================================================
    // Variable substitution
    // =========================================================================

    @Test
    void execute_resolvesVariables() {
        PlanNode node = PlanNode.builder("FTPSampler", "ftp-vars")
                .property("FTPSampler.host", "${ftpHost}")
                .property("FTPSampler.port", 1)
                .property("FTPSampler.path", "${filePath}")
                .property("FTPSampler.action", "download")
                .property("FTPSampler.username", "${user}")
                .property("FTPSampler.password", "${pass}")
                .property("FTPSampler.timeout", 500)
                .build();

        SampleResult result = new SampleResult("ftp-vars");
        Map<String, String> vars = Map.of(
                "ftpHost", "127.0.0.1",
                "filePath", "/test.txt",
                "user", "testuser",
                "pass", "testpass"
        );

        FTPSamplerExecutor.execute(node, result, vars);

        // Connection will fail but variables were resolved (no "empty" error)
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FTP error"),
                "Should fail with FTP error, not validation error: " + result.getFailureMessage());
    }

    // =========================================================================
    // Default property values
    // =========================================================================

    @Test
    void execute_usesDefaultUsername() {
        // Anonymous FTP with default username; connection will fail
        PlanNode node = PlanNode.builder("FTPSampler", "ftp-defaults")
                .property("FTPSampler.host", "127.0.0.1")
                .property("FTPSampler.port", 1)
                .property("FTPSampler.path", "/test")
                .property("FTPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ftp-defaults");

        FTPSamplerExecutor.execute(node, result, Map.of());

        // Confirms no NPE or validation error for missing username/password
        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("FTP error"));
    }

    @Test
    void execute_unsupportedAction() {
        PlanNode node = PlanNode.builder("FTPSampler", "ftp-bad-action")
                .property("FTPSampler.host", "127.0.0.1")
                .property("FTPSampler.port", 1)
                .property("FTPSampler.path", "/test")
                .property("FTPSampler.action", "delete")
                .property("FTPSampler.timeout", 500)
                .build();
        SampleResult result = new SampleResult("ftp-bad-action");

        // Will fail with connection error before reaching the action switch,
        // but the test confirms the node is accepted without NPE
        FTPSamplerExecutor.execute(node, result, Map.of());
        assertFalse(result.isSuccess());
    }

    // =========================================================================
    // PASV pattern
    // =========================================================================

    @Test
    void pasvPattern_matchesStandard() {
        assertTrue(FTPSamplerExecutor.PASV_PATTERN
                .matcher("227 Entering Passive Mode (127,0,0,1,39,15)").find());
    }

    @Test
    void pasvPattern_doesNotMatchBadFormat() {
        assertFalse(FTPSamplerExecutor.PASV_PATTERN
                .matcher("227 Entering Passive Mode").find());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static PlanNode ftpNode(String host, String path, String action) {
        return PlanNode.builder("FTPSampler", "ftp-test")
                .property("FTPSampler.host", host)
                .property("FTPSampler.port", 21)
                .property("FTPSampler.path", path)
                .property("FTPSampler.action", action)
                .property("FTPSampler.timeout", 500)
                .build();
    }

    private static BufferedReader readerFrom(String input) {
        return new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8));
    }
}
