package cn.com.mfish.graph.config;

import cn.com.mfish.graph.client.NebulaGraphClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * NebulaGraph 客户端配置
 * 自动注册 NebulaGraphClient Bean
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NebulaGraphProperties.class)
public class NebulaClientConfig {

    @Bean
    @ConditionalOnMissingBean(NebulaGraphClient.class)
    public NebulaGraphClient nebulaGraphClient(NebulaGraphProperties properties) {
        log.info("初始化 NebulaGraphClient，图空间: {}, 地址: {}", 
            properties.getSpace() != null ? properties.getSpace().getName() : "plm_graph", 
            properties.getAddresses());

        NebulaSessionPoolConfig poolConfig = new NebulaSessionPoolConfig();
        poolConfig.updateFromProperties(properties.getPool());

        NebulaGraphClient client = new NebulaGraphClient(properties, poolConfig);
        log.info("NebulaGraphClient 初始化完成，连接池大小: {}-{}", poolConfig.getMinIdle(), poolConfig.getMaxPoolSize());
        return client;
    }
}