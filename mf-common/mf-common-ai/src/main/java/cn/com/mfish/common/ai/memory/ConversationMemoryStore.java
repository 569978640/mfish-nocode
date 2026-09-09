package cn.com.mfish.common.ai.memory;

import java.util.List;

/**
 * 会话记忆存储（Conversation Memory Store）
 * <p>
 * Memory 模块的入口，负责按 sessionId 创建、获取、销毁 {@link ConversationMemory} 实例。
 * 对外暴露类似 Map 的语义，但底层可以是内存 / Redis / 数据库。
 * </p>
 * <p>
 * 使用方式：
 * </p>
 * <pre>
 * // 在请求入口获取（或创建）会话 Memory
 * ConversationMemory memory = memoryStore.getOrCreate(sessionId);
 *
 * // 注入上下文
 * memory.bindTenantContext(tenantContext);
 * memory.addDocumentChunks(chunks);
 * memory.updateVariable("biz.formId", 1001);
 *
 * // Planner / Assistant 读取上下文
 * String systemContext = memory.getSystemContext();
 *
 * // 会话结束后清理
 * memoryStore.remove(sessionId);
 * </pre>
 * <p>
 * 设计为 Spring Bean，由 {@code MemoryAutoConfiguration} 注册。
 * Controller / Assistant / Runtime 通过依赖注入获取。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
public interface ConversationMemoryStore {

    /**
     * 获取或创建会话 Memory
     * <p>
     * 首次访问某 sessionId 时创建新实例并缓存；后续访问返回同一实例。
     * 实例的生命周期由实现类管理（如基于 TTL 自动过期）。
     * </p>
     *
     * @param sessionId 会话ID（不能为 null 或空）
     * @return 会话 Memory 实例
     */
    ConversationMemory getOrCreate(String sessionId);

    /**
     * 获取会话 Memory（不创建）
     *
     * @param sessionId 会话ID
     * @return 会话 Memory 实例；不存在返回 null
     */
    ConversationMemory get(String sessionId);

    /**
     * 移除会话 Memory 并清理其内部状态
     * <p>
     * 触发 {@link ConversationMemory#clearAll()}，并从存储中移除。
     * 用于会话主动关闭、用户登出等场景。
     * </p>
     *
     * @param sessionId 会话ID
     * @return 被移除的实例；不存在返回 null
     */
    ConversationMemory remove(String sessionId);

    /**
     * 获取当前活跃的会话 ID 列表
     * <p>
     * 主要用于监控、调试、定期清理任务。
     * </p>
     *
     * @return 会话 ID 列表（不可变）
     */
    List<String> listSessionIds();

    /**
     * 当前存储的会话数量
     */
    int size();
}
