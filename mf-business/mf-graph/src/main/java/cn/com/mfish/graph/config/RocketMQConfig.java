package cn.com.mfish.graph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq.consumer")
public class RocketMQConfig {
    private String group = "plm-graph-sync-group";
    private String topic = "plm-graph-sync";
    private boolean enableDLQ = true;
    private int maxRetryTimes = 3;
}
