package cn.com.mfish.graph.disaster;

/**
 * 灾备策略接口
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface DisasterRecoveryStrategy {
    /**
     * 执行灾备切换
     *
     * @param reason 切换原因
     * @return true=切换成功
     */
    boolean switchover(String reason);

    /**
     * 执行故障恢复
     *
     * @return true=恢复成功
     */
    boolean recover();

    /**
     * 获取当前灾备状态
     *
     * @return 灾备状态
     */
    DisasterStatus getStatus();

    enum DisasterStatus {
        NORMAL,
        SWITCHING,
        FAILOVER,
        RECOVERING
    }
}