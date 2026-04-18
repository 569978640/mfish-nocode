package cn.com.mfish.graph.monitor;

/**
 * NebulaGraph 熔断器
 * 提供按业务线隔离、多维度熔断能力
 *
 * @author mfish
 * @date 2026-04-18
 */
public class NebulaCircuitBreaker {
    private final CircuitBreakerConfig config;
    private final String businessLine;
    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private long lastStateChangeTime;
    private int successCount = 0;
    private int failureCount = 0;
    private int halfOpenRequests = 0;

    public NebulaCircuitBreaker(CircuitBreakerConfig config) {
        this(config, "default");
    }

    public NebulaCircuitBreaker(CircuitBreakerConfig config, String businessLine) {
        this.config = config;
        this.businessLine = businessLine;
        this.lastStateChangeTime = System.currentTimeMillis();
    }

    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (shouldAttemptReset()) {
                    toHalfOpen();
                    return true;
                }
                return false;
            case HALF_OPEN:
                return halfOpenRequests < config.getHalfOpenRequests();
            default:
                return false;
        }
    }

    public void recordSuccess() {
        successCount++;
        halfOpenRequests++;

        if (state == CircuitBreakerState.HALF_OPEN) {
            if (halfOpenRequests >= config.getHalfOpenRequests() &&
                (double) successCount / halfOpenRequests >= config.getProbeRule().getSuccessThreshold()) {
                toClosed();
            }
        }
    }

    public void recordFailure() {
        failureCount++;
        halfOpenRequests = 0;

        if (state == CircuitBreakerState.HALF_OPEN) {
            toOpen();
        } else if (state == CircuitBreakerState.CLOSED) {
            int totalRequests = successCount + failureCount;
            if (totalRequests >= config.getMinRequestCount()) {
                double failureRate = (double) failureCount / totalRequests;
                if (failureRate >= config.getFailureRateThreshold()) {
                    toOpen();
                }
            }
        }
    }

    private boolean shouldAttemptReset() {
        return System.currentTimeMillis() - lastStateChangeTime >= config.getRecoveryTimeout() * 1000;
    }

    private void toOpen() {
        state = CircuitBreakerState.OPEN;
        lastStateChangeTime = System.currentTimeMillis();
        successCount = 0;
        failureCount = 0;
    }

    private void toHalfOpen() {
        state = CircuitBreakerState.HALF_OPEN;
        lastStateChangeTime = System.currentTimeMillis();
        halfOpenRequests = 0;
    }

    private void toClosed() {
        state = CircuitBreakerState.CLOSED;
        lastStateChangeTime = System.currentTimeMillis();
        successCount = 0;
        failureCount = 0;
    }

    public CircuitBreakerState getState() {
        return state;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public void persistState() {
        // 持久化熔断器状态
    }

    public void restoreState() {
        // 从持久化恢复状态
    }

    public enum CircuitBreakerState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static class CircuitBreakerConfig {
        private double failureRateThreshold = 0.5;
        private int minRequestCount = 10;
        private int recoveryTimeout = 60;
        private int halfOpenRequests = 10;
        private ProbeRule probeRule = new ProbeRule();

        public static class ProbeRule {
            private double successThreshold = 0.9;
        }
    }
}