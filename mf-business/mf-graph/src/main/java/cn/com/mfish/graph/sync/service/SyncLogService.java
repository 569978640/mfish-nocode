package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.model.event.GraphSyncEvent;

/**
 * 操作日志服务接口
 * 提供图同步操作的日志记录功能
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SyncLogService {

    /**
     * 记录跳过日志（幂等检查跳过）
     *
     * @param event 图同步事件
     * @param reason 跳过原因
     */
    void logSkip(GraphSyncEvent event, String reason);

    /**
     * 记录处理开始日志
     *
     * @param event 图同步事件
     */
    void logStart(GraphSyncEvent event);

    /**
     * 记录处理成功日志
     *
     * @param event 图同步事件
     * @param durationMs 处理耗时
     */
    void logSuccess(GraphSyncEvent event, long durationMs);

    /**
     * 记录处理失败日志
     *
     * @param event 图同步事件
     * @param durationMs 处理耗时
     * @param errorMessage 错误信息
     */
    void logFailed(GraphSyncEvent event, long durationMs, String errorMessage);
}