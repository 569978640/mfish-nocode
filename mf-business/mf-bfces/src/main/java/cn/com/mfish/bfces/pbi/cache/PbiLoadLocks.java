package cn.com.mfish.bfces.pbi.cache;

import org.springframework.stereotype.Component;

/**
 * @description: PBI缓存回源条纹锁。
 * 用固定数量的锁对象按lockKey哈希分配，替代"每个lockKey一个Object"的无界ConcurrentHashMap，
 * 避免lockKey含部件ID（如 part:{IDA2A2}）时锁对象随部件数永久增长。
 * 相同key恒映射到同一把锁；不同key可能共享一把锁，仅轻微放大并发串行粒度，
 * 调用方仍需在锁内执行双重检查以保证回源只发生一次。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Component
public class PbiLoadLocks {

    /** 条纹数量 */
    private static final int STRIPE_COUNT = 64;

    /** 固定锁对象数组，初始化后不再变化 */
    private final Object[] stripes = initStripes();

    private static Object[] initStripes() {
        Object[] locks = new Object[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    /**
     * 按lockKey获取稳定的回源锁对象。
     *
     * @param lockKey 回源锁标识（如 category:{分类}、part:{ID}、family:leaf:{分类}）
     * @return 对应的条纹锁对象（不为null）
     */
    public Object lockOf(String lockKey) {
        // floorMod保证负hashCode也映射到 0~STRIPE_COUNT-1
        return stripes[Math.floorMod(lockKey.hashCode(), STRIPE_COUNT)];
    }
}
