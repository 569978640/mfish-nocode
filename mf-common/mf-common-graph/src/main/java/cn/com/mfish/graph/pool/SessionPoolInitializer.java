package cn.com.mfish.graph.pool;

/**
 * NebulaGraph 会话池初始化器接口
 * 提供连接池的预热和懒加载策略
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SessionPoolInitializer {
    /**
     * 预热模式初始化
     * 启动时创建最小连接数
     */
    void warmUp();

    /**
     * 懒加载模式初始化
     * 按需创建连接
     *
     * @return SessionWrapper
     */
    SessionWrapper lazyLoad();

    /**
     * 初始化失败时的兜底策略
     *
     * @param strategy 兜底策略
     */
    void onInitFailure(DegradationStrategy strategy);

    /**
     * 降级策略接口
     */
    interface DegradationStrategy {
        /**
         * 执行降级操作
         *
         * @param operation 操作名称
         * @return 降级结果
         */
        Object degrade(String operation);
    }

    /**
     * 默认预热实现
     */
    class DefaultInitializer implements SessionPoolInitializer {
        private final NebulaSessionPool sessionPool;
        private final int minIdle;

        public DefaultInitializer(NebulaSessionPool sessionPool, int minIdle) {
            this.sessionPool = sessionPool;
            this.minIdle = minIdle;
        }

        @Override
        public void warmUp() {
            for (int i = 0; i < minIdle; i++) {
                try {
                    SessionWrapper wrapper = lazyLoad();
                    sessionPool.returnSession(wrapper);
                } catch (Exception e) {
                    // 忽略预热失败，继续
                }
            }
        }

        @Override
        public SessionWrapper lazyLoad() {
            return sessionPool.borrowSession();
        }

        @Override
        public void onInitFailure(DegradationStrategy strategy) {
            // 记录告警
        }
    }
}