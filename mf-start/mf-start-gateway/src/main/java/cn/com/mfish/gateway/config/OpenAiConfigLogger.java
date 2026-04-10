package cn.com.mfish.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * @description: OpenAI 配置日志器
 * @author: mfish
 * @date: 2026/4/10
 */
@Configuration
public class OpenAiConfigLogger {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiConfigLogger.class);

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String model;

    @PostConstruct
    public void logOpenAiConfig() {
        logger.info("===== OpenAI Config =====");
        logger.info("API Key: {}", apiKey != null && !apiKey.isEmpty() ? "****" + apiKey.substring(Math.max(0, apiKey.length() - 4)) : "NOT SET");
        logger.info("Base URL: {}", baseUrl != null && !baseUrl.isEmpty() ? baseUrl : "NOT SET");
        logger.info("Model: {}", model != null && !model.isEmpty() ? model : "NOT SET");
        logger.info("========================");
    }
}
