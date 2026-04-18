package cn.com.mfish.graph.schema;

import java.util.concurrent.TimeUnit;

/**
 * Schema 变更分布式锁接口
 * 防止并发变更导致 Schema 不一致
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SchemaChangeLock {
    /**
     * 尝试获取锁
     *
     * @param schemaName Schema 名称
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 锁 token，null 表示获取失败
     */
    String tryLock(String schemaName, long timeout, TimeUnit unit);

    /**
     * 释放锁
     *
     * @param schemaName Schema 名称
     * @param token 锁 token
     */
    void unlock(String schemaName, String token);

    /**
     * 检查是否有变更锁
     *
     * @param schemaName Schema 名称
     * @return true=有锁，false=无锁
     */
    boolean isLocked(String schemaName);

    /**
     * 默认实现（基于内存）
     * 注意：生产环境应使用分布式锁（如 Redis/ZooKeeper）
     */
    class InMemorySchemaChangeLock implements SchemaChangeLock {
        private final java.util.concurrent.ConcurrentHashMap<String, String> locks = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String tryLock(String schemaName, long timeout, TimeUnit unit) {
            String token = schemaName + ":" + System.nanoTime();
            String existing = locks.putIfAbsent(schemaName, token);
            if (existing == null) {
                return token;
            }
            return null;
        }

        @Override
        public void unlock(String schemaName, String token) {
            locks.remove(schemaName, token);
        }

        @Override
        public boolean isLocked(String schemaName) {
            return locks.containsKey(schemaName);
        }
    }
}