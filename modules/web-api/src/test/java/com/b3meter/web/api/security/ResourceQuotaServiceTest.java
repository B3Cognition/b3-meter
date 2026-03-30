package com.jmeternext.web.api.security;

import com.jmeternext.web.api.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceQuotaServiceTest {

    private TestRunRepository mockRunRepo;
    private ResourceQuotaService service;

    @BeforeEach
    void setUp() {
        mockRunRepo = mock(TestRunRepository.class);
        service = new ResourceQuotaService(mockRunRepo, 3, 10000, 14400);
    }

    @Test
    void passesWhenWithinAllQuotas() {
        when(mockRunRepo.countActive()).thenReturn(0);
        assertDoesNotThrow(() -> service.checkQuota("user1", 100, 3600));
    }

    @Test
    void throwsWhenConcurrentRunsExceeded() {
        when(mockRunRepo.countActive()).thenReturn(3);
        var ex = assertThrows(ResourceQuotaService.QuotaExceededException.class,
            () -> service.checkQuota("user1", 100, 3600));
        assertEquals("concurrent_runs", ex.getQuotaName());
    }

    @Test
    void throwsWhenVirtualUsersExceeded() {
        when(mockRunRepo.countActive()).thenReturn(0);
        var ex = assertThrows(ResourceQuotaService.QuotaExceededException.class,
            () -> service.checkQuota("user1", 20000, 3600));
        assertEquals("virtual_users", ex.getQuotaName());
    }

    @Test
    void throwsWhenDurationExceeded() {
        when(mockRunRepo.countActive()).thenReturn(0);
        var ex = assertThrows(ResourceQuotaService.QuotaExceededException.class,
            () -> service.checkQuota("user1", 100, 50000));
        assertEquals("duration", ex.getQuotaName());
    }

    @Test
    void quotaExceptionContainsLimit() {
        var ex = new ResourceQuotaService.QuotaExceededException("test", 42, "test message");
        assertEquals(42, ex.getLimit());
        assertEquals("test", ex.getQuotaName());
        assertEquals("test message", ex.getMessage());
    }
}
