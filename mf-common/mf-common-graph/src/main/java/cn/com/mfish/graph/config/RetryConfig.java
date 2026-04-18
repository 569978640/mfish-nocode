package cn.com.mfish.graph.config;

import lombok.Data;

/**
 * NebulaGraph 重试策略配置类
 * 配置指数退避重试的各项参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class RetryConfig {
    /**
     * 初始重试间隔(ms)，首次失败后等待时间
     */
    private long baseInterval = 1000;

    /**
     * 最大重试间隔(ms)，防止间隔过长
     */
    private long maxInterval = 4000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 重试指数（如 2.0 表示间隔翻倍）
     */
    private double multiplier = 2.0;

    /**
     * 批量失败告警阈值（失败率超过此值时告警）
     */
    private double batchFailureAlertThreshold = 0.5;

    /**
     * 幂等键续期间隔(s)，长批量操作定期续期防止过期
     */
    private int idempotentRenewInterval = 30;

    /**
     * 补偿失败告警阈值
     */
    private double compensateFailureAlertThreshold = 0.3;

    /**
     * 计算下一次重试间隔
     *
     * @param attemptCount 当前尝试次数
     * @return 下一次重试的间隔时间(ms)
     */
    public long getNextInterval(int attemptCount) {
        long interval = (long) (baseInterval * Math.pow(multiplier, attemptCount - 1));
        return Math.min(interval, maxInterval);
    }
}