package cn.com.mfish.graph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * NebulaGraph配置类
 * 用于配置NebulaGraph图数据库连接参数
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@Component
@ConfigurationProperties(prefix = "nebula")
public class NebulaConfig {
    /**
     * 单机模式配置
     */
    private SingleConfig single;

    /**
     * 集群模式配置
     */
    private ClusterConfig cluster;

    /**
     * 用户名，默认root
     */
    private String username = "root";

    /**
     * 密码，默认nebula
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
     * 单机模式配置
     */
    @Data
    public static class SingleConfig {
        /**
         * 是否启用单机模式
         */
        private boolean enabled = true;

        /**
         * 地址，格式：host:port
         */
        private String addresses;
    }

    /**
     * 集群模式配置
     */
    @Data
    public static class ClusterConfig {
        /**
         * 是否启用集群模式
         */
        private boolean enabled = false;

        /**
         * 地址列表，多个地址用逗号分隔
         */
        private String addresses;
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
         * 超时时间（毫秒）
         */
        private int timeout = 3000;

        /**
         * 空闲连接超时时间（秒）
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
         * 分片数
         */
        private int partitionNum = 100;
    }
}