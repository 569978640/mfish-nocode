package cn.com.mfish.graph.config;

import lombok.Data;

import java.util.Map;

/**
 * NebulaGraph 负载均衡配置类
 * 配置负载均衡策略、地址权重和故障检测参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class LoadBalanceConfig {
    /**
     * 负载均衡策略
     */
    private NebulaGraphProperties.LoadBalanceStrategy strategy = NebulaGraphProperties.LoadBalanceStrategy.FAILOVER;

    /**
     * 地址权重映射
     * key: 地址（host:port）
     * value: 权重值
     */
    private Map<String, Integer> weights;

    /**
     * 加权模式下权重刷新间隔(s)
     */
    private long weightRefreshInterval = 60;

    /**
     * 故障恢复检测间隔(s)
     */
    private long failureDetectionInterval = 10;

    /**
     * 连续失败次数阈值（超过后标记为不可用）
     */
    private int failureThreshold = 3;
}