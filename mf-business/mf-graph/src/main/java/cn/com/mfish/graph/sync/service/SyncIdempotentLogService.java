package cn.com.mfish.graph.sync.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.req.ReqSyncIdempotentLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;
import java.util.Date;

/**
 * @description: 幂等表
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
public interface SyncIdempotentLogService extends IService<SyncIdempotentLog> {
    /**
     * 分页列表查询
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    Result<PageResult<SyncIdempotentLog>> queryPageList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage);

    /**
     * 添加
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-添加结果
     */
    Result<SyncIdempotentLog> add(SyncIdempotentLog syncIdempotentLog);

    /**
     * 编辑
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-编辑结果
     */
    Result<SyncIdempotentLog> edit(SyncIdempotentLog syncIdempotentLog);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回幂等表-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回幂等表-批量删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回幂等表对象
     */
    Result<SyncIdempotentLog> queryById(String id);

    /**
     * 导出
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) throws IOException;

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
