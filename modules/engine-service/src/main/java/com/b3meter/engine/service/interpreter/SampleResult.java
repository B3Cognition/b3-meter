package com.jmeternext.engine.service.interpreter;

/**
 * Result of executing a single sampler node.
 *
 * <p>Carries all information needed to aggregate into a {@link com.jmeternext.engine.service.SampleBucket}:
 * timing, HTTP status, success flag, response body (for extractors and assertions), and label.
 *
 * <p>Only JDK types are used so that this class remains framework-free (Constitution Principle I).
 */
public final class SampleResult {

    private final String label;
    private boolean success;
    private int statusCode;
    private long connectTimeMs;
    private long latencyMs;
    private long totalTimeMs;
    private String responseBody;
    private String failureMessage;

    /**
     * Constructs a SampleResult.
     *
     * @param label sampler label (test name); must not be {@code null}
     */
    public SampleResult(String label) {
        if (label == null) throw new NullPointerException("label must not be null");
        this.label = label;
        this.success = true;
        this.responseBody = "";
        this.failureMessage = "";
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the sampler label. */
    public String getLabel() {
        return label;
    }

    /** Returns {@code true} if no assertion failed and the HTTP request succeeded. */
    public boolean isSuccess() {
        return success;
    }

    /** Sets the success flag. Assertions call this with {@code false} on mismatch. */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /** Returns the HTTP response status code, or 0 for non-HTTP samplers. */
    public int getStatusCode() {
        return statusCode;
    }

    /** Sets the HTTP response status code. */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /** Returns the TCP connect time in milliseconds. */
    public long getConnectTimeMs() {
        return connectTimeMs;
    }

    /** Sets the TCP connect time. */
    public void setConnectTimeMs(long connectTimeMs) {
        this.connectTimeMs = connectTimeMs;
    }

    /** Returns the time-to-first-byte (latency) in milliseconds. */
    public long getLatencyMs() {
        return latencyMs;
    }

    /** Sets the latency. */
    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    /** Returns the total elapsed time in milliseconds. */
    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    /** Sets the total elapsed time. */
    public void setTotalTimeMs(long totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }

    /** Returns the full response body as a string; never {@code null}. */
    public String getResponseBody() {
        return responseBody;
    }

    /** Sets the response body. */
    public void setResponseBody(String responseBody) {
        this.responseBody = (responseBody != null) ? responseBody : "";
    }

    /** Returns the failure message, or empty string if the sample succeeded. */
    public String getFailureMessage() {
        return failureMessage;
    }

    /** Sets a human-readable failure message. Also marks the sample as failed. */
    public void setFailureMessage(String failureMessage) {
        this.failureMessage = (failureMessage != null) ? failureMessage : "";
        this.success = false;
    }

    @Override
    public String toString() {
        return "SampleResult{label='" + label + "', success=" + success
                + ", statusCode=" + statusCode + ", totalTimeMs=" + totalTimeMs + "}";
    }
}
