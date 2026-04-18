package cn.com.mfish.graph.sync.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncOperationLog;
import cn.com.mfish.graph.sync.req.ReqSyncOperationLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 图同步操作日志
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
public interface SyncOperationLogService extends IService<SyncOperationLog> {
    /**
     * 分页列表查询
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @return 返回图同步操作日志-分页列表
     */
    Result<PageResult<SyncOperationLog>> queryPageList(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage);

    /**
     * 添加
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-添加结果
     */
    Result<SyncOperationLog> add(SyncOperationLog syncOperationLog);

    /**
     * 编辑
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-编辑结果
     */
    Result<SyncOperationLog> edit(SyncOperationLog syncOperationLog);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志-删除结果
     */
    Result<Boolean> delete(Long id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回图同步操作日志-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志对象
     */
    Result<SyncOperationLog> queryById(Long id);

    /**
     * 导出
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) throws IOException;

    /**
     * 记录接收日志
     *
     * @param event 图同步事件
     */
    void logReceive(GraphSyncEvent event);

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
