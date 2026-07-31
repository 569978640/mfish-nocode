package cn.com.mfish.common.ai.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Memory 模块自动配置
 * <p>
 * 注册 {@link ConversationMemoryStore} 的默认实现 {@link InMemoryConversationMemoryStore}。
 * </p>
 * <p>
 * 本模块只管理 Spring AI 管不到的两类上下文：Vars Context（变量/凭证）和
 * Document Context（文件解析块）。对话历史（Short-term Memory）由 Spring AI 的
 * {@code ChatMemory} + {@code MessageChatMemoryAdvisor} 自动管理，本模块不涉及。
 * </p>
 * <p>
 * 业务方可通过自定义 {@link ConversationMemoryStore} Bean 覆盖默认实现，
 * 例如替换为 Redis 版本以支持多实例部署。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
@Configuration
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConversationMemoryStore conversationMemoryStore() {
        return new InMemoryConversationMemoryStore();
    }
}
