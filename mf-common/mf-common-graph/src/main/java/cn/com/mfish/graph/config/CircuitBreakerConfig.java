package cn.com.mfish.graph.config;

import lombok.Data;

/**
 * NebulaGraph 熔断器配置类
 * 配置熔断器的各项阈值和探活规则
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class CircuitBreakerConfig {
    /**
     * 失败率阈值（超过此值时触发熔断）
     */
    private double failureRateThreshold = 0.5;

    /**
     * 最小请求数（达到此数量后才计算失败率，防止初期误判）
     */
    private int minRequestCount = 10;

    /**
     * 熔断恢复时间(s)，熔断后等待多久切换到半开状态
     */
    private int recoveryTimeout = 60;

    /**
     * 慢请求阈值(ms)，超过此时间的请求视为慢请求
     */
    private long slowRequestThreshold = 5000;

    /**
     * 连接池耗尽阈值（百分比，活跃连接占比超过此值时触发熔断）
     */
    private int poolExhaustThreshold = 80;

    /**
     * 半开状态放行请求数
     */
    private int halfOpenRequests = 10;

    /**
     * 半开状态成功阈值（成功率超过此值时关闭熔断）
     */
    private double halfOpenSuccessThreshold = 0.9;

    /**
     * 按业务线隔离（不同业务线使用独立熔断器）
     */
    private boolean isolateByBusinessLine = true;

    /**
     * 探活规则（半开状态下的探活配置）
     */
    private ProbeRule probeRule;

    /**
     * 状态持久化（重启后恢复熔断状态）
     */
    private boolean enableStatePersistence = true;

    /**
     * 探活规则配置
     */
    @Data
    public static class ProbeRule {
        /**
         * 探活请求数量（半开状态下放行的试探请求数）
         */
        private int probeCount = 10;

        /**
         * 探活成功阈值（成功率超过此值时关闭熔断）
         */
        private double successThreshold = 0.9;

        /**
         * 探活间隔(ms)，两次探活之间的间隔
         */
        private long probeIntervalMs = 1000;
    }
}