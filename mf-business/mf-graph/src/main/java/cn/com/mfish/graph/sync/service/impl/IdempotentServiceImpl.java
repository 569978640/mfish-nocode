package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.mapper.SyncIdempotentLogMapper;
import cn.com.mfish.graph.sync.service.IdempotentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

/**
 * 幂等服务实现
 * 基于 eventId 进行幂等检查和状态管理
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Service
public class IdempotentServiceImpl implements IdempotentService {

    private static final long PROCESSING_TIMEOUT_MS = 30 * 60 * 1000;

    @Autowired
    private SyncIdempotentLogMapper idempotentLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncIdempotentLog checkAndCreate(GraphSyncEvent event) {
        String eventId = event.getEventId();
        SyncIdempotentLog existing = idempotentLogMapper.selectForUpdate(eventId);

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
        }

        SyncIdempotentLog syncLog = new SyncIdempotentLog();
        syncLog.setId(existing == null ? UUID.randomUUID().toString().replace("-", "") : existing.getId());
        syncLog.setEventId(eventId);
        syncLog.setEventType(event.getEventType());
        syncLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        syncLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        syncLog.setStatus("PROCESSING");
        syncLog.setRetryCount(existing == null ? 0L : existing.getRetryCount() + 1);
        syncLog.setUpdateTime(new Date());

        idempotentLogMapper.insertOrUpdateIdempotent(
            syncLog.getId(),
            syncLog.getEventId(),
            syncLog.getEventType(),
            syncLog.getNodeCount(),
            syncLog.getEdgeCount(),
            syncLog.getStatus(),
            syncLog.getRetryCount(),
            syncLog.getUpdateTime(),
            syncLog.getCreateBy(),
            syncLog.getUpdateBy()
        );

        return idempotentLogMapper.selectForUpdate(eventId);
    }

    @Override
    public SyncIdempotentLog getByEventId(String eventId) {
        return idempotentLogMapper.selectOne(new LambdaQueryWrapper<SyncIdempotentLog>()
            .eq(SyncIdempotentLog::getEventId, eventId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(String eventId) {
        SyncIdempotentLog syncLog = getByEventId(eventId);
        if (syncLog != null) {
            syncLog.setStatus("COMPLETED");
            syncLog.setUpdateTime(new Date());
            idempotentLogMapper.updateById(syncLog);
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
            idempotentLogMapper.updateById(syncLog);
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
            idempotentLogMapper.updateById(syncLog);
        }
    }
}