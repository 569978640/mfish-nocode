package cn.com.mfish.common.ai.config;

import cn.com.mfish.common.ai.tool.FaultTolerantToolCallingManager;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description: Ai配置信息
 * @author: mfish
 * @date: 2025/8/18
 */
@Configuration
public class AiConfig {
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .build();
    }

    /**
     * 注册容错 ToolCallingManager，覆盖 Spring AI 默认的 DefaultToolCallingManager
     * <p>
     * <b>功能一：工具名容错</b>
     * 当 LLM 虚构了不存在的工具名时，返回提示信息（含可用工具列表）让 LLM 重新选择，
     * 而不是抛出 IllegalStateException 中断整个对话流程。
     * </p>
     * <p>
     * <b>功能二：按 Skill 声明顺序排序</b>
     * 当 LLM 在单次响应中返回多个 tool call 时，根据 guide 类型 Skill 的 toolOrder 声明
     * 对 tool calls 排序后执行。例如 leave-apply.md 声明
     * {@code toolOrder: frontend.navigate,demoLeaveApply.add,demoLeaveApply.submit,frontend.refresh}，
     * 即使 LLM 返回 refresh + add + submit，实际执行顺序仍为 add → submit → refresh。
     * </p>
     * <p>
     * <b>循环依赖规避</b>：
     * 此处不注入 ToolOrderProvider（避免构造器依赖形成循环）：
     * {@code llmModelRouter → openAiChatModel → toolCallingManager → skillCapabilityEngine
     * → skillChatClientProviderImpl → llmModelRouter}。
     * 改由 {@code CapabilityAutoConfiguration} 在 SkillCapabilityEngine 初始化后，
     * 通过 {@link FaultTolerantToolCallingManager#setToolOrderProvider} setter 注入。
     * 这样 ToolCallingManager 创建时只依赖 DefaultToolCallingManager，不触发 Skill 依赖链。
     * </p>
     * <p>
     * 通过 @ConditionalOnMissingBean 机制，此 Bean 会自动覆盖 Spring AI 的默认实现。
     * </p>
     */
    @Bean
    public FaultTolerantToolCallingManager toolCallingManager() {
        return new FaultTolerantToolCallingManager(DefaultToolCallingManager.builder().build());
    }
}
