package com.jmeternext.engine.service.interpreter;

import com.jmeternext.engine.service.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MailReaderSamplerExecutor}.
 */
class MailReaderSamplerExecutorTest {

    @Test
    void throwsOnNullNode() {
        SampleResult r = new SampleResult("mail");
        assertThrows(NullPointerException.class,
                () -> MailReaderSamplerExecutor.execute(null, r, Map.of()));
    }

    @Test
    void throwsOnNullResult() {
        PlanNode node = mailNode("mail.example.com");
        assertThrows(NullPointerException.class,
                () -> MailReaderSamplerExecutor.execute(node, null, Map.of()));
    }

    @Test
    void throwsOnNullVariables() {
        PlanNode node = mailNode("mail.example.com");
        SampleResult r = new SampleResult("mail");
        assertThrows(NullPointerException.class,
                () -> MailReaderSamplerExecutor.execute(node, r, null));
    }

    @Test
    void failsOnEmptyHost() {
        PlanNode node = PlanNode.builder("MailReaderSampler", "mail-empty")
                .build();

        SampleResult result = new SampleResult("mail-empty");
        MailReaderSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertTrue(result.getFailureMessage().contains("host is empty"));
    }

    @Test
    void returns501Stub() {
        PlanNode node = PlanNode.builder("MailReaderSampler", "mail-stub")
                .property("host", "imap.example.com")
                .property("port", "993")
                .property("protocol", "imaps")
                .property("username", "user@example.com")
                .property("password", "secret")
                .property("folder", "INBOX")
                .property("num_messages", "10")
                .build();

        SampleResult result = new SampleResult("mail-stub");
        MailReaderSamplerExecutor.execute(node, result, Map.of());

        assertFalse(result.isSuccess());
        assertEquals(501, result.getStatusCode());
        assertTrue(result.getResponseBody().contains("imap.example.com"));
        assertTrue(result.getResponseBody().contains("INBOX"));
        assertTrue(result.getResponseBody().contains("javax.mail"));
    }

    @Test
    void resolvesVariables() {
        PlanNode node = PlanNode.builder("MailReaderSampler", "mail-vars")
                .property("host", "${mail_host}")
                .property("username", "${mail_user}")
                .build();

        Map<String, String> vars = new HashMap<>();
        vars.put("mail_host", "resolved.mail.com");
        vars.put("mail_user", "resolved@example.com");

        SampleResult result = new SampleResult("mail-vars");
        MailReaderSamplerExecutor.execute(node, result, vars);

        assertTrue(result.getResponseBody().contains("resolved.mail.com"));
    }

    @Test
    void defaultPortForPop3() {
        PlanNode node = PlanNode.builder("MailReaderSampler", "mail-pop3")
                .property("host", "pop.example.com")
                .property("protocol", "pop3")
                .build();

        SampleResult result = new SampleResult("mail-pop3");
        MailReaderSamplerExecutor.execute(node, result, Map.of());

        assertTrue(result.getResponseBody().contains("Port: 110"));
    }

    private static PlanNode mailNode(String host) {
        return PlanNode.builder("MailReaderSampler", "mail-test")
                .property("host", host)
                .build();
    }
}
