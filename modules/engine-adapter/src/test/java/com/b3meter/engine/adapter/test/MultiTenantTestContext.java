package com.jmeternext.engine.adapter.test;

import com.jmeternext.engine.adapter.NoOpUIBridge;
import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.TestRunContextRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Shared test utility that creates N isolated {@link TestRunContext} instances and
 * registers them in the {@link TestRunContextRegistry} for multi-tenant test scenarios.
 *
 * <p>Use this class when a test must verify that concurrent run contexts maintain
 * fully independent state — for example, that writing to one context's runState or
 * resultWriters map does not affect any other context.
 *
 * <p>Each context is assigned a unique, randomly-generated runId to avoid collisions
 * between test runs.
 *
 * <p>This class implements {@link AutoCloseable} so it can be used in a
 * try-with-resources block; {@link #close()} deregisters all contexts from the
 * {@link TestRunContextRegistry}.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   try (MultiTenantTestContext mt = new MultiTenantTestContext(5)) {
 *       mt.get(0).putState("key", "value");
 *       assertNull(mt.get(1).getState("key")); // isolation holds
 *   } // contexts deregistered automatically
 * }</pre>
 *
 * <p>Thread-safety: individual {@link TestRunContext} instances are thread-safe.
 * The lifecycle methods ({@link #get}, {@link #close}) on this class are NOT
 * thread-safe and must be called from the test thread only.
 */
public final class MultiTenantTestContext implements AutoCloseable {

    private final List<TestRunContext> contexts;

    /**
     * Creates {@code tenantCount} isolated {@link TestRunContext} instances and
     * registers them in the {@link TestRunContextRegistry}.
     *
     * @param tenantCount number of isolated contexts to create; must be &gt;= 1
     * @throws IllegalArgumentException if {@code tenantCount} &lt; 1
     */
    public MultiTenantTestContext(int tenantCount) {
        if (tenantCount < 1) {
            throw new IllegalArgumentException("tenantCount must be >= 1, got: " + tenantCount);
        }

        List<TestRunContext> list = new ArrayList<>(tenantCount);
        for (int i = 0; i < tenantCount; i++) {
            TestRunContext ctx = TestRunContext.builder()
                    .runId(UUID.randomUUID().toString())
                    .planPath("test-plan-tenant-" + i + ".jmx")
                    .virtualUsers(1)
                    .durationSeconds(30)
                    .uiBridge(NoOpUIBridge.INSTANCE)
                    .build();
            TestRunContextRegistry.register(ctx);
            list.add(ctx);
        }
        this.contexts = Collections.unmodifiableList(list);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link TestRunContext} at the given zero-based index.
     *
     * @param index zero-based tenant index; must be in [0, tenantCount)
     * @return the context; never {@code null}
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     */
    public TestRunContext get(int index) {
        return contexts.get(index);
    }

    /**
     * Returns the number of isolated contexts managed by this instance.
     *
     * @return the tenant count; always &gt;= 1
     */
    public int tenantCount() {
        return contexts.size();
    }

    /**
     * Returns an unmodifiable view of all contexts in creation order.
     *
     * @return all contexts; never {@code null}
     */
    public List<TestRunContext> all() {
        return contexts;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Deregisters all contexts from the {@link TestRunContextRegistry}.
     *
     * <p>Called automatically when used in a try-with-resources block. Safe to call
     * multiple times (subsequent calls are no-ops because {@code registry.remove}
     * returns {@code null} for unknown ids).
     */
    @Override
    public void close() {
        for (TestRunContext ctx : contexts) {
            TestRunContextRegistry.remove(ctx.getRunId());
        }
    }
}
