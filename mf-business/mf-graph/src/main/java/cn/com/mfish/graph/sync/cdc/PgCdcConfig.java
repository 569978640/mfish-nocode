package cn.com.mfish.graph.sync.cdc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * PostgreSQL CDC 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "debezium.connector")
public class PgCdcConfig {
    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;
    private String slot;
    private String publication;
    private List<String> tables;
    private String redisAddress;
    private String redisPassword;
    private Integer redisDatabase;
    /**
     * Debezium Embedded 内部使用 Kafka Connect 框架，需要 bootstrap.servers 才能完成 WorkerConfig 初始化。
     * 实际数据通过 RocketMQ 转发，Kafka 仅作为占位地址，不会真正消费。
     */
    private String bootstrapServers;
}
