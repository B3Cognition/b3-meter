package com.jmeternext.web.api.security;

import com.jmeternext.web.api.repository.TestRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Enforces per-user resource quotas for test execution.
 * Configurable via application properties.
 */
@Service
public class ResourceQuotaService {

    private final TestRunRepository runRepository;

    @Value("${jmeter.quota.max-concurrent-runs:3}")
    private int maxConcurrentRuns;

    @Value("${jmeter.quota.max-virtual-users:10000}")
    private int maxVirtualUsers;

    @Value("${jmeter.quota.max-duration-seconds:14400}")
    private int maxDurationSeconds;

    @Autowired
    public ResourceQuotaService(TestRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    /**
     * Test-friendly constructor that allows setting quota limits directly.
     */
    ResourceQuotaService(TestRunRepository runRepository,
                         int maxConcurrentRuns,
                         int maxVirtualUsers,
                         int maxDurationSeconds) {
        this.runRepository = runRepository;
        this.maxConcurrentRuns = maxConcurrentRuns;
        this.maxVirtualUsers = maxVirtualUsers;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public void checkQuota(String userId, int requestedVUs, int requestedDurationSeconds) {
        int activeRuns = runRepository.countActive();
        if (activeRuns >= maxConcurrentRuns) {
            throw new QuotaExceededException("concurrent_runs", maxConcurrentRuns,
                "Maximum concurrent runs (" + maxConcurrentRuns + ") exceeded");
        }
        if (requestedVUs > maxVirtualUsers) {
            throw new QuotaExceededException("virtual_users", maxVirtualUsers,
                "Maximum virtual users (" + maxVirtualUsers + ") exceeded");
        }
        if (requestedDurationSeconds > maxDurationSeconds) {
            throw new QuotaExceededException("duration", maxDurationSeconds,
                "Maximum test duration (" + (maxDurationSeconds / 3600) + "h) exceeded");
        }
    }

    public int getMaxConcurrentRuns() { return maxConcurrentRuns; }
    public int getMaxVirtualUsers() { return maxVirtualUsers; }
    public int getMaxDurationSeconds() { return maxDurationSeconds; }

    public static class QuotaExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String quotaName;
        private final int limit;

        public QuotaExceededException(String quotaName, int limit, String message) {
            super(message);
            this.quotaName = quotaName;
            this.limit = limit;
        }

        public String getQuotaName() { return quotaName; }
        public int getLimit() { return limit; }
    }
}
