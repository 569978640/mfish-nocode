package cn.com.mfish.common.ai.capability;

import org.springframework.ai.chat.model.ChatModel;

/**
 * Skill ChatClient 提供者接口（跨模块解耦）
 * <p>
 * 提示词级 Skill 执行时需要调用 LLM，但 mf-common-ai 不依赖 mf-ai 模块的 LlmModelRouter。
 * 通过此接口由业务层注入按租户路由的 {@link ChatModel}，
 * SkillCapabilityEngine 用它构建 ChatClient 执行提示词模板。
 * </p>
 * <p>
 * 与 {@link SkillConfigProvider} 模式一致，均为跨模块解耦接口。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/22
 */
public interface SkillChatClientProvider {

    /**
     * 获取指定租户的 ChatModel
     * <p>
     * 租户路由逻辑由业务层 LlmModelRouter 实现，调用方无需关心。
     * </p>
     *
     * @param tenantId 租户ID（为空时返回全局默认模型）
     * @return 租户可用的 ChatModel
     */
    ChatModel getChatModel(String tenantId);
}
