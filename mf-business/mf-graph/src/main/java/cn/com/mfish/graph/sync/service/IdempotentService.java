package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;

/**
 * 幂等服务接口
 * 提供基于 eventId 的幂等检查和状态管理
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface IdempotentService {

    /**
     * 幂等检查并创建处理记录
     * 基于 eventId 进行幂等检查，防止重复消费
     *
     * @param event 图同步事件
     * @return null表示已处理过需要跳过，非null表示需要处理
     */
    SyncIdempotentLog checkAndCreate(GraphSyncEvent event);

    /**
     * 根据 eventId 查询幂等记录
     *
     * @param eventId 事件ID
     * @return 幂等记录
     */
    SyncIdempotentLog getByEventId(String eventId);

    /**
     * 标记处理成功
     *
     * @param eventId 事件ID
     */
    void markCompleted(String eventId);

    /**
     * 标记处理失败
     *
     * @param eventId 事件ID
     * @param errorMessage 错误信息
     * @param retryCount 重试次数
     */
    void markFailed(String eventId, String errorMessage, int retryCount);

    /**
     * 更新状态
     *
     * @param eventId 事件ID
     * @param status 状态
     * @param errorMessage 错误信息
     */
    void updateStatus(String eventId, String status, String errorMessage);
}