package cn.com.mfish.graph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * NebulaGraph 连接配置类
 * 配置连接地址、认证信息、负载均衡策略等参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
@ConfigurationProperties(prefix = "nebula")
public class NebulaGraphProperties {
    /**
     * 单节点配置
     */
    private SingleConfig single;

    /**
     * 集群配置
     */
    private ClusterConfig cluster;

    /**
     * 用户名
     */
    private String username = "root";

    /**
     * 密码
     */
    private String password = "nebula";

    /**
     * 连接池配置
     */
    private PoolConfig pool;

    /**
     * 图空间配置
     */
    private SpaceConfig space;

    /**
     * 获取有效地址列表
     * 根据 single/cluster 配置启用状态返回对应地址
     */
    public List<String> getAddresses() {
        if (single != null && single.isEnabled() && single.getAddresses() != null) {
            return List.of(single.getAddresses().split(","));
        }
        if (cluster != null && cluster.isEnabled() && cluster.getAddresses() != null) {
            return cluster.getAddresses();
        }
        return List.of("192.168.111.103:9669");
    }

    /**
     * 获取负载均衡策略，默认 FAILOVER
     */
    public LoadBalanceStrategy getLoadBalanceStrategy() {
        return LoadBalanceStrategy.FAILOVER;
    }

    /**
     * 单节点配置
     */
    @Data
    public static class SingleConfig {
        /**
         * 是否启用单节点模式
         */
        private boolean enabled = true;

        /**
         * 连接地址，格式 host:port
         */
        private String addresses = "192.168.111.103:9669";
    }

    /**
     * 集群配置
     */
    @Data
    public static class ClusterConfig {
        /**
         * 是否启用集群模式
         */
        private boolean enabled = false;

        /**
         * 集群地址列表
         */
        private List<String> addresses;
    }

    /**
     * 连接池配置
     */
    @Data
    public static class PoolConfig {
        /**
         * 最小连接数
         */
        private int minConns = 10;

        /**
         * 最大连接数
         */
        private int maxConns = 100;

        /**
         * 连接超时(ms)
         */
        private int timeout = 3000;

        /**
         * 空闲超时(s)
         */
        private int idleTimeout = 60;
    }

    /**
     * 图空间配置
     */
    @Data
    public static class SpaceConfig {
        /**
         * 图空间名称
         */
        private String name = "plm_graph";

        /**
         * 字符集
         */
        private String charset = "utf8";

        /**
         * 副本因子
         */
        private int replicaFactor = 1;

        /**
         * 分区数
         */
        private int partitionNum = 100;
    }

    /**
     * 负载均衡策略枚举
     */
    public enum LoadBalanceStrategy {
        /**
         * 轮询策略
         */
        ROUND_ROBIN,
        /**
         * 加权策略（基于节点负载）
         */
        WEIGHTED,
        /**
         * 故障优先策略（优先使用可用节点）
         */
        FAILOVER
    }
}