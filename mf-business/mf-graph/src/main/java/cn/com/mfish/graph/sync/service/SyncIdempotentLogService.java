package cn.com.mfish.graph.sync.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.req.ReqSyncIdempotentLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

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
     * @return 返回幂等表-删除结果
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
}
