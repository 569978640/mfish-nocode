package cn.com.mfish.graph.config;

import lombok.Data;

/**
 * NebulaGraph 会话池配置类
 * 配置会话池的大小、超时、拒绝策略等参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class NebulaSessionPoolConfig {
    /**
     * 最小空闲连接数（池中保持的最小连接数）
     */
    private int minIdle = 10;

    /**
     * 最大连接数（池中允许的最大连接数）
     */
    private int maxPoolSize = 100;

    /**
     * 空闲超时(s)，超过后释放空闲连接
     */
    private int idleTimeout = 60;

    /**
     * 心跳间隔(s)，保持连接活跃
     */
    private int heartbeatInterval = 30;

    /**
     * 连接最大生命周期(s)，到达后强制销毁重建
     */
    private int maxLifetime = 3600;

    /**
     * 借取等待超时(ms)，超时抛异常
     */
    private int borrowTimeout = 5000;

    /**
     * 拒绝策略：ABORT-抛异常 / DISCARD-丢弃请求 / RETURN_NULL-返回null
     */
    private RejectPolicy rejectPolicy = RejectPolicy.ABORT;

    /**
     * 初始化策略：EAGER-预热 / LAZY-懒加载
     */
    private InitStrategy initStrategy = InitStrategy.EAGER;

    /**
     * 扩缩容检测间隔(s)
     */
    private int scaleInterval = 60;

    /**
     * 扩容阈值（活跃连接占比 > 此值时扩容）
     */
    private double scaleUpThreshold = 0.8;

    /**
     * 缩容阈值（活跃连接占比 < 此值时缩容）
     */
    private double scaleDownThreshold = 0.3;

    /**
     * 活跃会话超时检测间隔(s)
     */
    private int activeSessionCheckInterval = 30;

    /**
     * 活跃会话超时时间(s)，超过后强制回收
     */
    private int activeSessionTimeout = 30;

    /**
     * 从 NebulaGraphProperties 的 pool 配置更新
     */
    public void updateFromProperties(NebulaGraphProperties.PoolConfig poolConfig) {
        if (poolConfig != null) {
            this.minIdle = poolConfig.getMinConns();
            this.maxPoolSize = poolConfig.getMaxConns();
            this.idleTimeout = poolConfig.getIdleTimeout();
            this.borrowTimeout = poolConfig.getTimeout();
        }
    }

    /**
     * 拒绝策略枚举
     */
    public enum RejectPolicy {
        /**
         * 抛异常
         */
        ABORT,
        /**
         * 丢弃请求
         */
        DISCARD,
        /**
         * 返回null
         */
        RETURN_NULL
    }

    /**
     * 初始化策略枚举
     */
    public enum InitStrategy {
        /**
         * 预热模式，启动时创建最小连接数
         */
        EAGER,
        /**
         * 懒加载模式，按需创建连接
         */
        LAZY
    }
}