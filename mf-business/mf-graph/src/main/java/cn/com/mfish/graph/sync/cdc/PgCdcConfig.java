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
}
