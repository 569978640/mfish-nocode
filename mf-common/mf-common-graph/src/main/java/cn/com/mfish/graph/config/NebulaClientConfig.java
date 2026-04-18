package cn.com.mfish.graph.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * NebulaGraph 客户端配置
 * 提供 RestTemplate 等通用组件
 *
 * @author mfish
 * @date 2026-04-18
 */
@Configuration
@EnableConfigurationProperties(NebulaConfig.class)
public class NebulaClientConfig {

    /**
     * RestTemplate Bean
     * 用于调用 NebulaGraph HTTP API
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}