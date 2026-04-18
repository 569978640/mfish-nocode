package cn.com.mfish.graph.config;

import lombok.Data;

/**
 * NebulaGraph 多地址高可用配置
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class NebulaHAConfig {
    /**
     * 故障检测间隔(s)
     */
    private long failureDetectionInterval = 10;

    /**
     * 连续失败次数阈值（超过后标记为不可用）
     */
    private int failureThreshold = 3;

    /**
     * 恢复检测间隔(s)
     */
    private long recoveryDetectionInterval = 30;

    /**
     * 故障转移超时(ms)
     */
    private long failoverTimeout = 5000;

    /**
     * 是否启用自动故障转移
     */
    private boolean enableAutoFailover = true;

    /**
     * 是否启用熔断器
     */
    private boolean enableCircuitBreaker = true;

    /**
     * 熔断器失败率阈值
     */
    private double circuitBreakerFailureRateThreshold = 0.5;

    /**
     * 熔断器恢复超时(s)
     */
    private int circuitBreakerRecoveryTimeout = 60;
}