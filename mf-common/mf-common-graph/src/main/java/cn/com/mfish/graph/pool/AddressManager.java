package cn.com.mfish.graph.pool;

import java.util.Set;

/**
 * NebulaGraph 地址管理器接口
 * 管理多地址场景下的地址可用性状态，支持故障检测和恢复
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface AddressManager {
    /**
     * 连续失败次数阈值（超过此值标记为不可用）
     */
    int FAILURE_THRESHOLD = 3;

    /**
     * 不可用地址恢复检测间隔(s)
     */
    long RECOVERY_INTERVAL = 30;

    /**
     * 记录地址失败次数，连续失败超阈值则标记为不可用
     *
     * @param address 地址
     */
    void recordFailure(String address);

    /**
     * 获取可用地址（优先从可用地址中选择）
     *
     * @return 可用地址
     */
    String getAvailableAddress();

    /**
     * 标记地址为可用
     *
     * @param address 地址
     */
    void markAvailable(String address);

    /**
     * 标记地址为不可用
     *
     * @param address 地址
     */
    void markUnavailable(String address);

    /**
     * 获取所有不可用地址列表
     *
     * @return 不可用地址集合
     */
    Set<String> getUnavailableAddresses();

    /**
     * 获取所有可用地址列表
     *
     * @return 可用地址列表
     */
    java.util.List<String> getAvailableAddresses();

    /**
     * 获取地址失败次数
     *
     * @param address 地址
     * @return 失败次数
     */
    int getFailureCount(String address);

    /**
     * 重置地址失败计数
     *
     * @param address 地址
     */
    void resetFailureCount(String address);

    /**
     * 检查地址是否可用
     *
     * @param address 地址
     * @return true=可用，false=不可用
     */
    boolean isAvailable(String address);
}