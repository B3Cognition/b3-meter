package com.jmeternext.engine.service.plan;

/**
 * Checked exception thrown when {@link JmxTreeWalker} encounters an
 * unrecoverable structural or syntactic problem in a JMX file.
 *
 * <p>Wraps the underlying {@link javax.xml.stream.XMLStreamException} when the
 * failure is a low-level XML parse error, or is thrown directly for structural
 * violations (e.g. missing root element).
 */
public final class JmxParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public JmxParseException(String message) {
        super(message);
    }

    public JmxParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
