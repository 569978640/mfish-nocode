package cn.com.mfish.graph.retry;

import lombok.extern.slf4j.Slf4j;

/**
 * 重试执行器
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class RetryExecutor {
    private final RetryStrategy retryStrategy;
    private final int maxRetries;

    public RetryExecutor(RetryStrategy retryStrategy, int maxRetries) {
        this.retryStrategy = retryStrategy;
        this.maxRetries = maxRetries;
    }

    public RetryExecutor() {
        this(new ExponentialBackoffRetryStrategy(), 3);
    }

    /**
     * 执行带重试的操作
     *
     * @param operation 操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws Exception 所有重试失败后抛出最后一个异常
     */
    public <T> T execute(RetryOperation<T> operation) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.warn("操作失败（尝试 {}/{}）: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    long interval = retryStrategy.getNextInterval(attempt);
                    log.debug("等待 {}ms 后重试...", interval);
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }

        throw lastException;
    }

    @FunctionalInterface
    public interface RetryOperation<T> {
        T execute() throws Exception;
    }
}