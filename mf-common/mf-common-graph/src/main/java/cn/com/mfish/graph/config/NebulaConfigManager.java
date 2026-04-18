package cn.com.mfish.graph.config;

import lombok.Getter;

/**
 * NebulaGraph 配置管理器
 * 统一管理所有配置，支持动态更新
 *
 * @author mfish
 * @date 2026-04-18
 */
@Getter
public class NebulaConfigManager {
    private NebulaGraphProperties graphProperties;
    private NebulaSessionPoolConfig poolConfig;
    private RetryConfig retryConfig;
    private CircuitBreakerConfig circuitBreakerConfig;
    private LoadBalanceConfig loadBalanceConfig;
    private NebulaQuotaConfig quotaConfig;
    private NebulaHAConfig haConfig;

    public static NebulaConfigManager getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final NebulaConfigManager INSTANCE = new NebulaConfigManager();
    }

    public void init(NebulaGraphProperties graphProperties,
                    NebulaSessionPoolConfig poolConfig,
                    RetryConfig retryConfig,
                    CircuitBreakerConfig circuitBreakerConfig,
                    LoadBalanceConfig loadBalanceConfig,
                    NebulaQuotaConfig quotaConfig,
                    NebulaHAConfig haConfig) {
        this.graphProperties = graphProperties;
        this.poolConfig = poolConfig;
        this.retryConfig = retryConfig;
        this.circuitBreakerConfig = circuitBreakerConfig;
        this.loadBalanceConfig = loadBalanceConfig;
        this.quotaConfig = quotaConfig;
        this.haConfig = haConfig;
    }

    public void updateGraphProperties(NebulaGraphProperties graphProperties) {
        this.graphProperties = graphProperties;
    }

    public void updatePoolConfig(NebulaSessionPoolConfig poolConfig) {
        this.poolConfig = poolConfig;
    }
}