package cn.com.mfish.graph.retry;

/**
 * 重试策略接口
 *
 * @author mfish
 * @date 2026-04-18
 */
@FunctionalInterface
public interface RetryStrategy {
    /**
     * 计算下一次重试间隔
     *
     * @param attemptCount 当前尝试次数（从1开始）
     * @return 下一次重试的间隔时间(ms)
     */
    long getNextInterval(int attemptCount);
}