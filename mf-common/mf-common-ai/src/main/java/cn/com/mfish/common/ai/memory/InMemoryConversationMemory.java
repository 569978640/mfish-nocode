package cn.com.mfish.common.ai.memory;

import cn.com.mfish.common.ai.agent.TenantContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JVM 内存的 {@link ConversationMemory} 默认实现
 * <p>
 * 两段记忆的存储策略：
 * <ul>
 *   <li><b>Vars Context</b>：使用 {@link ConcurrentHashMap} 存储变量，支持并发读写。
 *       值为 Object，业务方自行约定类型契约。</li>
 *   <li><b>Document Context</b>：使用 {@link List} + 同步锁保护，因为文档块通常批量写入、
 *       顺序读出。按 chunkId 去重。</li>
 * </ul>
 * </p>
 * <p>
 * <b>不管理 Short-term Memory</b>：对话历史由 Spring AI 的 {@code ChatMemory} +
 * {@code MessageChatMemoryAdvisor} 自动管理，本类不涉及，避免双写不一致。
 * </p>
 * <p>
 * 线程安全：本类所有方法线程安全，可被多个线程（请求线程 + 异步编排线程）并发访问。
 * 实例由 {@link InMemoryConversationMemoryStore} 按 sessionId 单例化，无需关心实例级并发。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
@Slf4j
public class InMemoryConversationMemory implements ConversationMemory {

    /**
     * Vars Context 中租户上下文的标准 key 前缀
     */
    private static final String TENANT_KEY_PREFIX = "sys.tenant.";
    /** tenantId 在 Vars Context 中的 key */
    public static final String KEY_TENANT_ID = "sys.tenant.tenantId";
    /** userId 在 Vars Context 中的 key */
    public static final String KEY_USER_ID = "sys.tenant.userId";
    /** accessToken 在 Vars Context 中的 key */
    public static final String KEY_ACCESS_TOKEN = "sys.tenant.accessToken";
    /** requestAttributes 在 Vars Context 中的 key（Servlet） */
    public static final String KEY_REQUEST_ATTRIBUTES = "sys.tenant.requestAttributes";
    /** serverWebExchange 在 Vars Context 中的 key（WebFlux） */
    public static final String KEY_SERVER_WEB_EXCHANGE = "sys.tenant.serverWebExchange";

    private final String sessionId;

    /** Vars Context：业务变量 + 租户上下文，统一存储 */
    private final Map<String, Object> varsContext = new ConcurrentHashMap<>();

    /** Document Context：文件解析块列表，按 chunkIndex 顺序维护 */
    private final List<DocumentChunk> documentChunks = new ArrayList<>();

    /** 文档块去重索引：chunkId → 是否已存在 */
    private final Map<String, Boolean> chunkIndex = new ConcurrentHashMap<>();

    public InMemoryConversationMemory(String sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId 不能为 null");
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    // ==================== Vars Context ====================

    @Override
    public void updateVariable(String key, Object value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (value == null) {
            varsContext.remove(key);
        } else {
            varsContext.put(key, value);
        }
    }

    @Override
    public Object getVariable(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return varsContext.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> targetType) {
        Object value = getVariable(key);
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        log.warn("[Memory] 变量类型不匹配 key={} expected={} actual={}",
                key, targetType.getName(), value.getClass().getName());
        return null;
    }

    @Override
    public Map<String, Object> getVarsContext() {
        // 返回不可变快照，外部修改需通过 updateVariable
        return Collections.unmodifiableMap(new LinkedHashMap<>(varsContext));
    }

    @Override
    public void bindTenantContext(TenantContext tenantContext) {
        if (tenantContext == null) {
            // 清除绑定的租户信息
            varsContext.remove(KEY_TENANT_ID);
            varsContext.remove(KEY_USER_ID);
            varsContext.remove(KEY_ACCESS_TOKEN);
            varsContext.remove(KEY_REQUEST_ATTRIBUTES);
            varsContext.remove(KEY_SERVER_WEB_EXCHANGE);
            return;
        }
        varsContext.put(KEY_TENANT_ID, tenantContext.getTenantId());
        varsContext.put(KEY_USER_ID, tenantContext.getUserId());
        varsContext.put(KEY_ACCESS_TOKEN, tenantContext.getAccessToken());
        if (tenantContext.getRequestAttributes() != null) {
            varsContext.put(KEY_REQUEST_ATTRIBUTES, tenantContext.getRequestAttributes());
        }
        if (tenantContext.getServerWebExchange() != null) {
            varsContext.put(KEY_SERVER_WEB_EXCHANGE, tenantContext.getServerWebExchange());
        }
    }

    @Override
    public void clearVars() {
        varsContext.clear();
    }

    // ==================== Document Context ====================

    @Override
    public void addDocumentChunks(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        synchronized (documentChunks) {
            for (DocumentChunk chunk : chunks) {
                if (chunk == null || chunk.getChunkId() == null) {
                    continue;
                }
                // chunkId 去重
                if (chunkIndex.putIfAbsent(chunk.getChunkId(), Boolean.TRUE) != null) {
                    log.debug("[Memory] 文档块已存在，跳过 chunkId={} fileName={}",
                            chunk.getChunkId(), chunk.getFileName());
                    continue;
                }
                documentChunks.add(chunk);
            }
            // 按 chunkIndex 字段排序，保证顺序稳定
            documentChunks.sort((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()));
        }
    }

    @Override
    public List<DocumentChunk> getDocumentChunks() {
        synchronized (documentChunks) {
            return new ArrayList<>(documentChunks);
        }
    }

    @Override
    public String getDocumentContext() {
        List<DocumentChunk> snapshot;
        synchronized (documentChunks) {
            if (documentChunks.isEmpty()) {
                return "";
            }
            snapshot = new ArrayList<>(documentChunks);
        }
        StringBuilder sb = new StringBuilder();
        for (DocumentChunk chunk : snapshot) {
            String fileName = chunk.getFileName() != null ? chunk.getFileName() : chunk.getFileKey();
            sb.append("=== 文件: ").append(fileName).append(" ===\n");
            sb.append(chunk.getContent() != null ? chunk.getContent() : "");
            sb.append("\n=== 文件结束: ").append(fileName).append(" ===\n\n");
        }
        return sb.toString();
    }

    @Override
    public void clearDocuments() {
        synchronized (documentChunks) {
            documentChunks.clear();
            chunkIndex.clear();
        }
    }

    // ==================== 上下文拼接 ====================

    @Override
    public String getSystemContext() {
        StringBuilder sb = new StringBuilder();

        // 拼接 Vars Context（仅展示 sys.tenant.* 和非内部变量）
        Map<String, Object> vars = getVarsContext();
        String tenantContext = buildTenantSection(vars);
        if (!tenantContext.isEmpty()) {
            sb.append("【用户上下文】\n").append(tenantContext).append("\n");
        }

        String bizVars = buildBizVarsSection(vars);
        if (!bizVars.isEmpty()) {
            sb.append("【业务变量】\n").append(bizVars).append("\n");
        }

        // 拼接 Document Context
        String docContext = getDocumentContext();
        if (!docContext.isEmpty()) {
            sb.append("【上传文件】\n").append(docContext);
        }

        return sb.toString();
    }

    /**
     * 构建租户上下文段落
     */
    private String buildTenantSection(Map<String, Object> vars) {
        Object tenantId = vars.get(KEY_TENANT_ID);
        Object userId = vars.get(KEY_USER_ID);
        if (tenantId == null && userId == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (tenantId != null) {
            sb.append("租户: ").append(tenantId).append("\n");
        }
        if (userId != null) {
            sb.append("用户: ").append(userId).append("\n");
        }
        return sb.toString();
    }

    /**
     * 构建业务变量段落（过滤掉 sys.tenant.* 内部变量和 requestAttributes/exchange 等非文本对象）
     */
    private String buildBizVarsSection(Map<String, Object> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach((key, value) -> {
            // 跳过租户上下文相关 key（已在用户上下文段落展示）
            if (key.startsWith(TENANT_KEY_PREFIX)) {
                return;
            }
            // 跳过非文本对象（RequestAttributes / ServerWebExchange 等）
            if (value == null || value.getClass().getName().startsWith("org.springframework.web")) {
                return;
            }
            // 跳过 Class 对象
            if (value instanceof Class) {
                return;
            }
            sb.append(key).append(": ").append(value).append("\n");
        });
        return sb.toString();
    }

    @Override
    public void clearAll() {
        // 仅清空本模块管理的两段记忆；Spring AI 的 ChatMemory 不在此清理
        clearVars();
        clearDocuments();
    }
}
