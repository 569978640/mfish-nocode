package cn.com.mfish.graph.pool;

import java.util.List;

/**
 * NebulaGraph 会话池监控接口
 * 提供连接池状态监控、泄漏检测等能力
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SessionPoolMonitor {
    /**
     * 获取当前空闲连接数
     *
     * @return 空闲连接数
     */
    int getIdleCount();

    /**
     * 获取当前活跃连接数
     *
     * @return 活跃连接数
     */
    int getActiveCount();

    /**
     * 获取等待借取的任务数（队列积压）
     *
     * @return 等待任务数
     */
    int getWaitingTasks();

    /**
     * 获取平均借取等待时间(ms)
     *
     * @return 平均等待时间
     */
    long getAvgBorrowWaitTime();

    /**
     * 获取连接创建失败次数
     *
     * @return 失败次数
     */
    long getConnectionCreateFailures();

    /**
     * 获取心跳失败次数
     *
     * @return 失败次数
     */
    long getHeartbeatFailures();

    /**
     * 获取连接复用率（复用量/总借取量）
     *
     * @return 复用率
     */
    double getConnectionReuseRate();

    /**
     * 获取借取超时次数
     *
     * @return 超时次数
     */
    long getBorrowTimeouts();

    /**
     * 获取连接创建总耗时(ms)
     *
     * @return 总耗时
     */
    long getConnectionCreateTime();

    /**
     * 获取连接销毁数量
     *
     * @return 销毁数量
     */
    long getConnectionDestroyCount();

    /**
     * 获取预热失败次数
     *
     * @return 失败次数
     */
    long getWarmupFailures();

    /**
     * 检测泄漏的 Session（活跃超时的 Session）
     *
     * @param timeoutMs 超时阈值(ms)
     * @return 泄漏的 Session 列表
     */
    List<SessionWrapper> detectLeakedSessions(long timeoutMs);

    /**
     * 强制回收泄漏的 Session
     *
     * @param timeoutMs 超时阈值(ms)
     */
    void reclaimLeakedSessions(long timeoutMs);

    /**
     * 记录借取等待时间
     *
     * @param waitTime 等待时间(ms)
     */
    void recordBorrowWaitTime(long waitTime);

    /**
     * 记录连接创建时间
     *
     * @param createTime 创建时间(ms)
     */
    void recordConnectionCreateTime(long createTime);

    /**
     * 记录连接创建失败
     */
    void recordConnectionCreateFailure();

    /**
     * 记录心跳失败
     */
    void recordHeartbeatFailure();

    /**
     * 记录借取超时
     */
    void recordBorrowTimeout();

    /**
     * 记录预热失败
     */
    void recordWarmupFailure();

    /**
     * 记录连接销毁
     */
    void recordConnectionDestroy();

    /**
     * 获取连接复用次数
     *
     * @return 复用次数
     */
    long getConnectionReuseCount();

    /**
     * 获取总借取次数
     *
     * @return 总借取次数
     */
    long getTotalBorrowCount();
}