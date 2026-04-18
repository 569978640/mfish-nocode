package cn.com.mfish.graph.pool;

import cn.com.mfish.graph.config.NebulaSessionPoolConfig;

/**
 * NebulaGraph 会话池动态扩缩容接口
 * 根据负载情况自动调整连接池大小
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SessionPoolScaler {
    /**
     * 检查是否应该扩容
     *
     * @return true=应该扩容
     */
    boolean shouldScaleUp();

    /**
     * 检查是否应该缩容
     *
     * @return true=应该缩容
     */
    boolean shouldScaleDown();

    /**
     * 执行扩容
     *
     * @return 扩容后的大小
     */
    int scaleUp();

    /**
     * 执行缩容
     *
     * @return 缩容后的大小
     */
    int scaleDown();

    /**
     * 默认扩缩容实现
     */
    class DefaultScaler implements SessionPoolScaler {
        private final NebulaSessionPoolConfig config;
        private final SessionPoolMonitor monitor;
        private int currentSize;
        private long lastScaleTime = 0;
        private static final long SCALE_COOLDOWN_MS = 60000;

        public DefaultScaler(NebulaSessionPoolConfig config, SessionPoolMonitor monitor, int currentSize) {
            this.config = config;
            this.monitor = monitor;
            this.currentSize = currentSize;
        }

        @Override
        public boolean shouldScaleUp() {
            if (currentSize >= config.getMaxPoolSize()) {
                return false;
            }
            if (System.currentTimeMillis() - lastScaleTime < SCALE_COOLDOWN_MS) {
                return false;
            }
            int activeCount = monitor.getActiveCount();
            int maxSize = config.getMaxPoolSize();
            double ratio = (double) activeCount / maxSize;
            return ratio > config.getScaleUpThreshold();
        }

        @Override
        public boolean shouldScaleDown() {
            if (currentSize <= config.getMinIdle()) {
                return false;
            }
            if (System.currentTimeMillis() - lastScaleTime < SCALE_COOLDOWN_MS) {
                return false;
            }
            int activeCount = monitor.getActiveCount();
            int idleCount = monitor.getIdleCount();
            if (idleCount < config.getMinIdle()) {
                return false;
            }
            double ratio = (double) activeCount / currentSize;
            return ratio < config.getScaleDownThreshold();
        }

        @Override
        public int scaleUp() {
            lastScaleTime = System.currentTimeMillis();
            int newSize = Math.min(currentSize + 1, config.getMaxPoolSize());
            currentSize = newSize;
            return newSize;
        }

        @Override
        public int scaleDown() {
            lastScaleTime = System.currentTimeMillis();
            int newSize = Math.max(currentSize - 1, config.getMinIdle());
            currentSize = newSize;
            return newSize;
        }

        /**
         * 获取当前连接池大小
         *
         * @return 当前大小
         */
        public int getCurrentSize() {
            return currentSize;
        }
    }
}