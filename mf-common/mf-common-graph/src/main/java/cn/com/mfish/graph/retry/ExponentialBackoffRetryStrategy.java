package cn.com.mfish.graph.retry;

/**
 * 指数退避重试策略
 *
 * @author mfish
 * @date 2026-04-18
 */
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    private final long baseInterval;
    private final long maxInterval;
    private final double multiplier;

    public ExponentialBackoffRetryStrategy(long baseInterval, long maxInterval, double multiplier) {
        this.baseInterval = baseInterval;
        this.maxInterval = maxInterval;
        this.multiplier = multiplier;
    }

    public ExponentialBackoffRetryStrategy(long baseInterval, long maxInterval) {
        this(baseInterval, maxInterval, 2.0);
    }

    public ExponentialBackoffRetryStrategy() {
        this(1000, 4000, 2.0);
    }

    @Override
    public long getNextInterval(int attemptCount) {
        long interval = (long) (baseInterval * Math.pow(multiplier, attemptCount - 1));
        return Math.min(interval, maxInterval);
    }
}