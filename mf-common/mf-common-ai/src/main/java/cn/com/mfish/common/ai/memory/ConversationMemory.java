package cn.com.mfish.common.ai.memory;

import cn.com.mfish.common.ai.agent.TenantContext;

import java.util.List;
import java.util.Map;

/**
 * 会话级记忆容器（Conversation Memory）
 * <p>
 * 三驾马车驱动的 AI 架构 —— Memory 模块的核心抽象。
 * 每个 sessionId 对应一个 ConversationMemory 实例，聚合会话内 Spring AI 管不到的两类上下文：
 * </p>
 * <ol>
 *   <li><b>Vars Context（变量上下文）</b>：低代码平台运行实例中的局部变量、用户凭证（Token）、
 *       租户上下文等。统一收纳原本散落在 TenantContext / ToolContext / 业务变量中的数据，
 *       通过 {@link #updateVariable} / {@link #getVariable} 读写，{@link #getVarsContext}
 *       供 Planner 一次性快照。</li>
 *   <li><b>Document Context（文档上下文）</b>：由 FileParseService 解析后的临时文件块（Chunk）。
 *       通过 {@link #addDocumentChunks} 批量注入，{@link #getDocumentChunks} 读取，
 *       {@link #getDocumentContext} 直接产出 LLM 可读的拼接文本。</li>
 * </ol>
 * <p>
 * <b>为什么不管理 Short-term Memory（Chat 历史）？</b>
 * Spring AI 的 {@code ChatMemory} + {@code MessageChatMemoryAdvisor} 已经完整覆盖
 * "对话历史存储 + 自动注入 LLM 上下文" 两个职责，本模块不再重复实现，避免双写不一致。
 * Planner / Assistant 若需要读取对话历史，直接注入 Spring AI 的 {@code ChatMemory} 即可。
 * </p>
 * <p>
 * 接口设计原则：
 * <ul>
 *   <li><b>会话隔离</b>：所有方法以当前实例绑定的 sessionId 为作用域，不跨会话泄漏</li>
 *   <li><b>两段独立</b>：Vars 和 Documents 互不耦合，可分别清理（{@link #clearVars} / {@link #clearDocuments}）</li>
 *   <li><b>上下文拼接</b>：{@link #getSystemContext()} 一次性产出 LLM 系统提示词所需的全部上下文片段</li>
 *   <li><b>不耦合存储</b>：本接口只定义读写契约，底层存储由实现类决定（内存/Redis/DB）</li>
 * </ul>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
public interface ConversationMemory {

    /**
     * 获取当前会话 ID
     */
    String getSessionId();

    // ==================== Vars Context ====================

    /**
     * 更新一个变量到 Vars Context
     * <p>
     * 若变量已存在则覆盖。常见用法：
     * </p>
     * <ul>
     *   <li>低代码平台运行时写入局部变量（如 `userId` / `formId` / `businessKey`）</li>
     *   <li>认证拦截器写入用户凭证（`accessToken` / `refreshToken`）</li>
     *   <li>编排流程写入中间结果（如 Planner 产出的 `currentPlan` / Executor 写入的 `stepResults`）</li>
     * </ul>
     *
     * @param key   变量名（建议使用命名空间前缀避免冲突，如 `sys.userId` / `biz.formId`）
     * @param value 变量值（可为 null，表示移除该变量）
     */
    void updateVariable(String key, Object value);

    /**
     * 读取一个变量
     *
     * @param key 变量名
     * @return 变量值；不存在返回 null
     */
    Object getVariable(String key);

    /**
     * 读取一个变量并按指定类型转换
     *
     * @param key        变量名
     * @param targetType 目标类型
     * @param <T>        泛型
     * @return 转换后的值；不存在或类型不匹配返回 null
     */
    <T> T getVariable(String key, Class<T> targetType);

    /**
     * 获取 Vars Context 的快照（只读视图）
     * <p>
     * Planner 拼接上下文时调用，避免逐个 getVariable 的开销。
     * 返回的 Map 不可变，修改需通过 {@link #updateVariable}。
     * </p>
     *
     * @return 变量快照（不可变 Map）
     */
    Map<String, Object> getVarsContext();

    /**
     * 绑定租户上下文到 Vars Context
     * <p>
     * 将 TenantContext 的 5 个字段（tenantId / userId / accessToken / requestAttributes /
     * serverWebExchange）按约定的 key 写入 Vars Context，供后续工具调用恢复认证态。
     * </p>
     * <p>
     * 这是对原 AgentRuntime.captureTenantContext + BaseAssistant.buildToolContext 流程的统一收纳：
     * 原本散落在两处的"捕获-传递-解包"逻辑，现在统一为 Memory 的一次 bind 调用。
     * </p>
     *
     * @param tenantContext 租户上下文快照（null 表示清除绑定的租户信息）
     */
    void bindTenantContext(TenantContext tenantContext);

    /**
     * 清空 Vars Context
     */
    void clearVars();

    // ==================== Document Context ====================

    /**
     * 批量添加文档块到 Document Context
     * <p>
     * FileParseService 解析文件后调用此方法注入。重复添加相同 chunkId 的块会被去重。
     * </p>
     *
     * @param chunks 文档块列表（不能为 null，可为空列表）
     */
    void addDocumentChunks(List<DocumentChunk> chunks);

    /**
     * 获取当前会话的全部文档块
     * <p>
     * 返回的是副本或不可变视图，外部修改不影响内部状态。
     * </p>
     *
     * @return 文档块列表（可能为空，不会为 null）
     */
    List<DocumentChunk> getDocumentChunks();

    /**
     * 获取 Document Context 拼接后的文本
     * <p>
     * 按 chunkIndex 顺序拼接所有文档块内容，每个块带文件名分隔符。
     * Planner / Assistant 注入 LLM 上下文时调用此方法，无需自己拼接。
     * </p>
     * <p>
     * <b>全量注入语义</b>：本方法返回当前会话的全部文档块内容，适合文档量较小的场景。
     * 文档量较大时（几十份 PDF / 大型代码库），建议改用 {@link #searchDocumentContext} 按相关性检索，
     * 避免 LLM 上下文窗口爆炸。
     * </p>
     * <p>
     * 示例输出：
     * </p>
     * <pre>
     * === 文件: example.docx ===
     * 文件内容...
     * === 文件结束: example.docx ===
     * </pre>
     *
     * @return 拼接后的文本；无文档时返回空字符串
     */
    String getDocumentContext();

    /**
     * 按相关性检索文档块（向量库扩展点）
     * <p>
     * <b>默认实现（内存版）</b>：忽略 query，返回全部文档块（截断到 topK）。
     * 这等价于"全量注入"，保持与 {@link #getDocumentChunks()} 一致的行为。
     * </p>
     * <p>
     * <b>向量库版实现</b>：将 query 文本向量化，按余弦相似度返回最相关的 topK 个 chunk。
     * 这是 RAG（检索增强生成）的核心入口，让 LLM 只看到最相关的文档片段而非全部文档。
     * </p>
     * <p>
     * 调用方建议：
     * <ul>
     *   <li>文档量小（< 5 个文件或 < 20K 字符）：用 {@link #getDocumentChunks()} 全量注入</li>
     *   <li>文档量大：用 {@code searchDocuments(prompt, 5)} 检索 topK 个最相关 chunk</li>
     * </ul>
     * </p>
     *
     * @param query 检索查询文本（通常是用户 prompt 或当前任务描述）；内存版可忽略此参数
     * @param topK  返回的最大 chunk 数；&lt;= 0 表示不限制（返回全部）
     * @return 检索到的文档块列表（按相关性或 chunkIndex 排序）；可能为空，不会为 null
     */
    default List<DocumentChunk> searchDocuments(String query, int topK) {
        List<DocumentChunk> all = getDocumentChunks();
        if (topK <= 0 || all.size() <= topK) {
            return all;
        }
        return new java.util.ArrayList<>(all.subList(0, topK));
    }

    /**
     * 按相关性检索并拼接为 LLM 上下文文本（向量库扩展点）
     * <p>
     * 默认实现：调用 {@link #searchDocuments(query, topK)} 检索，然后按
     * {@link #getDocumentContext()} 的格式拼接。
     * </p>
     * <p>
     * 向量库版可重写此方法以使用更高效的批量检索 API，或在拼接时附加相关性分数。
     * </p>
     *
     * @param query 检索查询文本；内存版可忽略
     * @param topK  返回的最大 chunk 数；&lt;= 0 表示不限制
     * @return 拼接后的文本；无文档或检索无结果时返回空字符串
     */
    default String searchDocumentContext(String query, int topK) {
        List<DocumentChunk> chunks = searchDocuments(query, topK);
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            String fileName = chunk.getFileName() != null ? chunk.getFileName() : chunk.getFileKey();
            sb.append("=== 文件: ").append(fileName).append(" ===\n");
            sb.append(chunk.getContent() != null ? chunk.getContent() : "");
            sb.append("\n=== 文件结束: ").append(fileName).append(" ===\n\n");
        }
        return sb.toString();
    }

    /**
     * 清空 Document Context
     */
    void clearDocuments();

    // ==================== 上下文拼接 ====================

    /**
     * 一次性产出 LLM 系统提示词所需的全部上下文
     * <p>
     * Planner 在构建 prompt 时调用此方法，获取 Vars Context 和 Document Context 的拼接文本，
     * 注入到 system prompt 中。Short-term Memory（Chat 历史）由 Spring AI
     * MessageChatMemoryAdvisor 自动注入，不在此方法产出范围内。
     * </p>
     * <p>
     * 输出格式示例：
     * </p>
     * <pre>
     * 【用户上下文】
     * 租户: tenant_001
     * 用户: user_123
     *
     * 【业务变量】
     * formId: 1001
     * businessKey: ORDER-2026-001
     *
     * 【上传文件】
     * === 文件: example.docx ===
     * 文件内容...
     * === 文件结束: example.docx ===
     * </pre>
     *
     * @return 系统上下文文本；无任何上下文时返回空字符串
     */
    String getSystemContext();

    /**
     * 清空所有记忆（Vars + Documents）
     * <p>
     * 用于会话彻底销毁场景。会话本身的存储条目由 {@link ConversationMemoryStore} 管理。
     * </p>
     * <p>
     * 注意：此方法不清空 Spring AI 的 ChatMemory（对话历史），那部分由 Spring AI
     * 自己管理，会话结束时业务方需另行调用 {@code ChatMemory.clear(sessionId)}。
     * </p>
     */
    void clearAll();
}
