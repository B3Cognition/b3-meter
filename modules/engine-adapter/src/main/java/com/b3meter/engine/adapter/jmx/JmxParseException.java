package com.jmeternext.engine.adapter.jmx;

public class JmxParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public JmxParseException(String message) { super(message); }
    public JmxParseException(String message, Throwable cause) { super(message, cause); }
}
