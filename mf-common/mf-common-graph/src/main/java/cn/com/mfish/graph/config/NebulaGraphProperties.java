package cn.com.mfish.graph.config;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * NebulaGraph Graph 连接配置类
 * 配置连接地址、认证信息、负载均衡策略等参数
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class NebulaGraphProperties {
    /**
     * 连接地址列表（支持多地址，高可用）
     * 格式：host:port，多个地址用逗号分隔
     */
    private List<String> addresses;

    /**
     * 用户名
     */
    private String username = "root";

    /**
     * 密码
     */
    private String password = "nebula";

    /**
     * 图空间名称
     */
    private String space = "plm_graph";

    /**
     * 连接超时(ms)
     */
    private int connectTimeout = 3000;

    /**
     * 读写超时(ms)
     */
    private int socketTimeout = 30000;

    /**
     * 重试次数
     */
    private int retry = 3;

    /**
     * 负载均衡策略
     */
    private LoadBalanceStrategy loadBalanceStrategy = LoadBalanceStrategy.FAILOVER;

    /**
     * 地址权重配置
     * key: 地址（host:port）
     * value: 权重值
     */
    private Map<String, Integer> addressWeights;

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