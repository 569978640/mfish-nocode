package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.req.ReqSyncIdempotentLog;
import cn.com.mfish.graph.sync.mapper.SyncIdempotentLogMapper;
import cn.com.mfish.graph.sync.service.SyncIdempotentLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
* @description: 幂等表
* @author: mfish
* @date: 2026-04-18
* @version: V2.3.1
*/
@Slf4j
@Service
public class SyncIdempotentLogServiceImpl extends ServiceImpl<SyncIdempotentLogMapper, SyncIdempotentLog> implements SyncIdempotentLogService {

    private static final long PROCESSING_TIMEOUT_MS = 30 * 60 * 1000;

    /**
     * 分页列表查询
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    @Override
    public Result<PageResult<SyncIdempotentLog>> queryPageList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqSyncIdempotentLog, reqPage)), "幂等表-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    private List<SyncIdempotentLog> queryList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        return list();
    }

    /**
     * 添加
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-添加结果
     */
    @Override
    public Result<SyncIdempotentLog> add(SyncIdempotentLog syncIdempotentLog) {
        if (save(syncIdempotentLog)) {
            return Result.ok(syncIdempotentLog, "幂等表-添加成功!");
        }
        return Result.fail(syncIdempotentLog, "错误:幂等表-添加失败!");
    }

    /**
     * 编辑
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-编辑结果
     */
    @Override
    public Result<SyncIdempotentLog> edit(SyncIdempotentLog syncIdempotentLog) {
        if (updateById(syncIdempotentLog)) {
            return Result.ok(syncIdempotentLog, "幂等表-编辑成功!");
        }
        return Result.fail(syncIdempotentLog, "错误:幂等表-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回幂等表-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "幂等表-删除成功!");
        }
        return Result.fail(false, "错误:幂等表-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回幂等表-批量删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "幂等表-批量删除成功!");
        }
        return Result.fail(false, "错误:幂等表-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回幂等表对象
     */
    @Override
    public Result<SyncIdempotentLog> queryById(String id) {
        SyncIdempotentLog syncIdempotentLog = getById(id);
        return Result.ok(syncIdempotentLog, "幂等表-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) throws IOException {
        ExcelUtils.write("幂等表_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqSyncIdempotentLog, reqPage));
    }

    @Override
    public SyncIdempotentLog checkAndCreate(GraphSyncEvent event) {
        String eventId = event.getEventId();
        SyncIdempotentLog existing = getByEventId(eventId);

        if (existing != null) {
            if ("COMPLETED".equals(existing.getStatus())) {
                log.info("事件已处理完成，跳过: eventId={}", eventId);
                return null;
            }
            if ("PROCESSING".equals(existing.getStatus())) {
                if (existing.getUpdateTime() != null &&
                    System.currentTimeMillis() - existing.getUpdateTime().getTime() < PROCESSING_TIMEOUT_MS) {
                    log.warn("事件正在处理中，防止重复消费: eventId={}", eventId);
                    return null;
                }
                log.warn("事件处理超时，允许重试: eventId={}", eventId);
            }
            if ("FAILED".equals(existing.getStatus())) {
                log.info("事件处理失败，允许重试: eventId={}", eventId);
            }
        }

        SyncIdempotentLog syncLog = new SyncIdempotentLog();
        syncLog.setEventId(eventId);
        syncLog.setEventType(event.getEventType());
        syncLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        syncLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        syncLog.setStatus("PROCESSING");
        syncLog.setRetryCount(existing == null ? 0L : existing.getRetryCount() + 1);
        syncLog.setUpdateTime(new Date());

        if (existing == null) {
            save(syncLog);
        } else {
            syncLog.setId(existing.getId());
            updateById(syncLog);
        }

        return syncLog;
    }

    @Override
    public SyncIdempotentLog getByEventId(String eventId) {
        return getOne(new LambdaQueryWrapper<SyncIdempotentLog>().eq(SyncIdempotentLog::getEventId, eventId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(String eventId) {
        SyncIdempotentLog syncLog = getByEventId(eventId);
        if (syncLog != null) {
            syncLog.setStatus("COMPLETED");
            syncLog.setUpdateTime(new Date());
            updateById(syncLog);
            log.info("标记事件处理成功: eventId={}", eventId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String eventId, String errorMessage, int retryCount) {
        SyncIdempotentLog syncLog = getByEventId(eventId);
        if (syncLog != null) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(errorMessage);
            syncLog.setRetryCount((long) retryCount);
            syncLog.setUpdateTime(new Date());
            updateById(syncLog);
            log.error("标记事件处理失败: eventId={}, error={}", eventId, errorMessage);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String eventId, String status, String errorMessage) {
        SyncIdempotentLog syncLog = getByEventId(eventId);
        if (syncLog != null) {
            syncLog.setStatus(status);
            if (errorMessage != null) {
                syncLog.setErrorMessage(errorMessage);
            }
            syncLog.setUpdateTime(new Date());
            updateById(syncLog);
        }
    }
}
