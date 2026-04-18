package cn.com.mfish.graph.idempotent;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存幂等键管理器（单机版）
 * 生产环境应使用 Redis 等分布式存储
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class InMemoryIdempotentKeyManager implements IdempotentKeyManager {
    private final Map<String, Long> keyStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public InMemoryIdempotentKeyManager() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredKeys, 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public String generateKey(String operation, String bizId) {
        return operation + ":" + bizId + ":" + System.currentTimeMillis();
    }

    @Override
    public boolean recordKey(String key, long ttlSeconds) {
        if (keyStore.putIfAbsent(key, System.currentTimeMillis() + ttlSeconds * 1000) == null) {
            log.debug("记录幂等键: key={}, ttl={}s", key, ttlSeconds);
            return true;
        }
        return false;
    }

    @Override
    public boolean exists(String key) {
        Long expiration = keyStore.get(key);
        if (expiration == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiration) {
            keyStore.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void deleteKey(String key) {
        keyStore.remove(key);
        log.debug("删除幂等键: key={}", key);
    }

    @Override
    public boolean renewKey(String key, long ttlSeconds) {
        if (keyStore.containsKey(key)) {
            keyStore.put(key, System.currentTimeMillis() + ttlSeconds * 1000);
            return true;
        }
        return false;
    }

    private void cleanupExpiredKeys() {
        long now = System.currentTimeMillis();
        keyStore.entrySet().removeIf(entry -> entry.getValue() < now);
        log.debug("清理过期幂等键完成，当前数量: {}", keyStore.size());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}