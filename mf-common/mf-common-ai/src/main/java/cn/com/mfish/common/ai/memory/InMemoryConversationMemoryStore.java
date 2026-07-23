package cn.com.mfish.common.ai.memory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于 JVM 内存的 {@link ConversationMemoryStore} 默认实现
 * <p>
 * 使用 {@link ConcurrentHashMap} 按 sessionId 缓存 {@link ConversationMemory} 实例。
 * 实例首次访问时创建并缓存，后续访问返回同一实例（sessionId 级单例）。
 * </p>
 * <p>
 * 限制：进程内存储，重启丢失；不支持分布式部署下的会话漂移。
 * 后续可替换为 Redis 实现以支持多实例共享。
 * </p>
 * <p>
 * 注意：本 Store 只管理 Vars Context 和 Document Context，不管理 Spring AI 的
 * ChatMemory（对话历史）。会话结束时如需清空对话历史，业务方需另行调用
 * {@code ChatMemory.clear(sessionId)}。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/20
 */
public class InMemoryConversationMemoryStore implements ConversationMemoryStore {

    private final ConcurrentMap<String, ConversationMemory> store = new ConcurrentHashMap<>();

    public InMemoryConversationMemoryStore() {
    }

    @Override
    public ConversationMemory getOrCreate(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId 不能为 null");
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空字符串");
        }
        return store.computeIfAbsent(sessionId, InMemoryConversationMemory::new);
    }

    @Override
    public ConversationMemory get(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return store.get(sessionId);
    }

    @Override
    public ConversationMemory remove(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        ConversationMemory removed = store.remove(sessionId);
        if (removed != null) {
            removed.clearAll();
        }
        return removed;
    }

    @Override
    public List<String> listSessionIds() {
        return store.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return store.size();
    }
}
