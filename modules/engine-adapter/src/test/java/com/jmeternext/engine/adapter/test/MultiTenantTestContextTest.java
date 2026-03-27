package com.jmeternext.engine.adapter.test;

import com.jmeternext.engine.service.TestRunContext;
import com.jmeternext.engine.service.TestRunContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MultiTenantTestContext}.
 */
class MultiTenantTestContextTest {

    @AfterEach
    void cleanRegistry() {
        // Guard: ensure registry is clean even if a test fails before close()
        TestRunContextRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void tenantCountMatchesRequested() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(5)) {
            assertEquals(5, mt.tenantCount());
        }
    }

    @Test
    void allContextsAreNonNull() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            for (int i = 0; i < 3; i++) {
                assertNotNull(mt.get(i));
            }
        }
    }

    @Test
    void allRunIdsAreUnique() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(10)) {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                assertTrue(ids.add(mt.get(i).getRunId()),
                        "Duplicate runId at index " + i);
            }
        }
    }

    @Test
    void allContextsAreRegisteredInRegistry() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(4)) {
            for (int i = 0; i < 4; i++) {
                String runId = mt.get(i).getRunId();
                assertNotNull(TestRunContextRegistry.get(runId),
                        "Context " + i + " should be in the registry");
            }
        }
    }

    @Test
    void singleTenantIsAllowed() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(1)) {
            assertEquals(1, mt.tenantCount());
            assertNotNull(mt.get(0));
        }
    }

    // -------------------------------------------------------------------------
    // get() — index access
    // -------------------------------------------------------------------------

    @Test
    void getReturnsDistinctContextsAtEachIndex() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            assertNotSame(mt.get(0), mt.get(1));
            assertNotSame(mt.get(1), mt.get(2));
            assertNotSame(mt.get(0), mt.get(2));
        }
    }

    @Test
    void getThrowsOutOfBoundsForNegativeIndex() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> mt.get(-1));
        }
    }

    @Test
    void getThrowsOutOfBoundsWhenIndexEqualsCount() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            assertThrows(IndexOutOfBoundsException.class, () -> mt.get(3));
        }
    }

    // -------------------------------------------------------------------------
    // all() — bulk access
    // -------------------------------------------------------------------------

    @Test
    void allReturnsListWithCorrectSize() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(6)) {
            List<TestRunContext> all = mt.all();
            assertEquals(6, all.size());
        }
    }

    @Test
    void allReturnsUnmodifiableList() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(2)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> mt.all().add(null));
        }
    }

    // -------------------------------------------------------------------------
    // Isolation — independent state
    // -------------------------------------------------------------------------

    @Test
    void writingToOneContextDoesNotAffectAnother() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            mt.get(0).putState("key", "value-from-0");

            assertNull(mt.get(1).getState("key"),
                    "context 1 should not see state written to context 0");
            assertNull(mt.get(2).getState("key"),
                    "context 2 should not see state written to context 0");
        }
    }

    @Test
    void eachContextMaintainsItsOwnState() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(4)) {
            for (int i = 0; i < 4; i++) {
                mt.get(i).putState("index", i);
            }
            for (int i = 0; i < 4; i++) {
                assertEquals(i, mt.get(i).getState("index"),
                        "context " + i + " should have its own state value");
            }
        }
    }

    @Test
    void writingResultWriterToOneContextDoesNotAffectOthers() {
        try (MultiTenantTestContext mt = new MultiTenantTestContext(3)) {
            mt.get(0).putResultWriter("output.jtl", () -> {});

            assertEquals(0, mt.get(1).resultWriterCount(),
                    "context 1 should have no result writers");
            assertEquals(0, mt.get(2).resultWriterCount(),
                    "context 2 should have no result writers");
        }
    }

    // -------------------------------------------------------------------------
    // close() — deregistration
    // -------------------------------------------------------------------------

    @Test
    void closeDeregistersAllContextsFromRegistry() {
        MultiTenantTestContext mt = new MultiTenantTestContext(4);
        List<String> runIds = mt.all().stream()
                .map(TestRunContext::getRunId)
                .toList();

        mt.close();

        for (String runId : runIds) {
            assertNull(TestRunContextRegistry.get(runId),
                    "context " + runId + " should be deregistered after close()");
        }
    }

    @Test
    void tryWithResourcesDeregistersAutomatically() {
        String runId;
        try (MultiTenantTestContext mt = new MultiTenantTestContext(1)) {
            runId = mt.get(0).getRunId();
            assertNotNull(TestRunContextRegistry.get(runId));
        }
        assertNull(TestRunContextRegistry.get(runId),
                "context should be deregistered after try-with-resources exits");
    }

    @Test
    void closeIsIdempotent() {
        MultiTenantTestContext mt = new MultiTenantTestContext(2);
        mt.close();
        // Second close should not throw
        assertDoesNotThrow(mt::close);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    void zeroTenantCountThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new MultiTenantTestContext(0));
    }

    @Test
    void negativeTenantCountThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new MultiTenantTestContext(-1));
    }
}
