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

import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes a {@code MailReaderSampler} {@link PlanNode}.
 *
 * <p>Reads the following sampler properties:
 * <ul>
 *   <li>{@code host} — mail server hostname</li>
 *   <li>{@code port} — mail server port (default: 993 for IMAP, 110 for POP3)</li>
 *   <li>{@code username} — login username</li>
 *   <li>{@code password} — login password</li>
 *   <li>{@code folder} — mailbox folder (default "INBOX")</li>
 *   <li>{@code protocol} — mail protocol: imap, imaps, pop3, pop3s</li>
 *   <li>{@code num_messages} — number of messages to read (default -1 = all)</li>
 *   <li>{@code delete} — whether to delete messages after reading</li>
 *   <li>{@code store_mime_message} — store raw MIME content</li>
 * </ul>
 *
 * <p>This is a STUB implementation. Full mail reading requires javax.mail / jakarta.mail
 * on the classpath. The stub parses all JMX properties so imported test plans do not crash,
 * and returns a 501 (Not Implemented) result.
 *
 * <p>Only JDK types are used (Constitution Principle I: framework-free).
 */
public final class MailReaderSamplerExecutor {

    private static final Logger LOG = Logger.getLogger(MailReaderSamplerExecutor.class.getName());

    private MailReaderSamplerExecutor() {
        // utility class -- not instantiable
    }

    /**
     * Executes the mail reader operation described by {@code node}.
     *
     * @param node      the MailReaderSampler node; must not be {@code null}
     * @param result    the sample result to populate; must not be {@code null}
     * @param variables current VU variable scope
     */
    public static void execute(PlanNode node, SampleResult result, Map<String, String> variables) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(variables, "variables must not be null");

        String host = resolve(node.getStringProp("host", ""), variables);
        String protocol = resolve(node.getStringProp("protocol", "imap"), variables).toLowerCase();
        int defaultPort = protocol.contains("imap") ? 993 : 110;
        int port = node.getIntProp("port", defaultPort);
        String username = resolve(node.getStringProp("username", ""), variables);
        String password = resolve(node.getStringProp("password", ""), variables);
        String folder = resolve(node.getStringProp("folder", "INBOX"), variables);
        int numMessages = node.getIntProp("num_messages", -1);
        boolean delete = node.getBoolProp("delete");
        boolean storeMime = node.getBoolProp("store_mime_message");

        if (host.isBlank()) {
            result.setFailureMessage("MailReaderSampler: host is empty");
            return;
        }

        LOG.log(Level.WARNING,
                "MailReaderSamplerExecutor: Mail reader not available -- requires javax.mail. "
                + "Host={0}:{1}, Protocol={2}, Folder={3}",
                new Object[]{host, port, protocol, folder});

        result.setStatusCode(501);
        result.setResponseBody(
                "Mail Reader sampler is a stub. Requires javax.mail / jakarta.mail on the classpath.\n"
                + "Host: " + host + "\n"
                + "Port: " + port + "\n"
                + "Protocol: " + protocol + "\n"
                + "Username: " + username + "\n"
                + "Folder: " + folder + "\n"
                + "Messages to read: " + (numMessages == -1 ? "all" : String.valueOf(numMessages)) + "\n"
                + "Delete after read: " + delete + "\n"
                + "Store MIME: " + storeMime);
        result.setFailureMessage("Mail reader requires javax.mail on classpath");
    }

    private static String resolve(String value, Map<String, String> variables) {
        if (value == null) return "";
        return VariableResolver.resolve(value, variables);
    }
}
