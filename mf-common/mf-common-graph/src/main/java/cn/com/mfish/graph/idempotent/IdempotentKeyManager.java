package cn.com.mfish.graph.idempotent;

/**
 * 幂等键管理器接口
 * 管理幂等键的存储、校验和过期
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface IdempotentKeyManager {
    /**
     * 生成幂等键
     *
     * @param operation 操作类型
     * @param bizId 业务ID
     * @return 幂等键
     */
    String generateKey(String operation, String bizId);

    /**
     * 记录幂等键
     *
     * @param key 幂等键
     * @param ttlSeconds 过期时间(秒)
     * @return true=记录成功
     */
    boolean recordKey(String key, long ttlSeconds);

    /**
     * 检查幂等键是否存在
     *
     * @param key 幂等键
     * @return true=已存在（重复操作）
     */
    boolean exists(String key);

    /**
     * 删除幂等键
     *
     * @param key 幂等键
     */
    void deleteKey(String key);

    /**
     * 续期幂等键
     *
     * @param key 幂等键
     * @param ttlSeconds 新过期时间(秒)
     * @return true=续期成功
     */
    boolean renewKey(String key, long ttlSeconds);
}