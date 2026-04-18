package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncOperationLog;
import cn.com.mfish.graph.sync.mapper.SyncOperationLogMapper;
import cn.com.mfish.graph.sync.service.SyncLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 操作日志服务实现
 * 记录图同步操作的日志
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Service
public class SyncLogServiceImpl implements SyncLogService {

    @Autowired
    private SyncOperationLogMapper operationLogMapper;

    @Override
    public void logSkip(GraphSyncEvent event, String reason) {
        log.info("[MQ跳过] eventId={}, reason={}", event.getEventId(), reason);

        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("SKIP");
        opLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        opLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        opLog.setStartTime(new Date());
        opLog.setEndTime(new Date());
        opLog.setStatus("SKIPPED");
        opLog.setErrorMessage(reason);
        operationLogMapper.insert(opLog);
    }

    @Override
    public void logStart(GraphSyncEvent event) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("PROCESS");
        opLog.setNodeCount(event.getNodes() == null ? 0L : (long) event.getNodes().size());
        opLog.setEdgeCount(event.getEdges() == null ? 0L : (long) event.getEdges().size());
        opLog.setStartTime(new Date());
        opLog.setStatus("PROCESSING");
        operationLogMapper.insert(opLog);
        log.debug("[处理开始] eventId={}", event.getEventId());
    }

    @Override
    public void logSuccess(GraphSyncEvent event, long durationMs) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("COMPLETE");
        opLog.setStartTime(new Date(System.currentTimeMillis() - durationMs));
        opLog.setEndTime(new Date());
        opLog.setDurationMs(durationMs);
        opLog.setStatus("SUCCESS");
        operationLogMapper.insert(opLog);

        log.info("[处理成功] eventId={}, duration={}ms", event.getEventId(), durationMs);
    }

    @Override
    public void logFailed(GraphSyncEvent event, long durationMs, String errorMessage) {
        SyncOperationLog opLog = new SyncOperationLog();
        opLog.setEventId(event.getEventId());
        opLog.setOperation("FAILED");
        opLog.setStartTime(new Date(System.currentTimeMillis() - durationMs));
        opLog.setEndTime(new Date());
        opLog.setDurationMs(durationMs);
        opLog.setStatus("FAILED");
        opLog.setErrorMessage(errorMessage);
        operationLogMapper.insert(opLog);

        log.error("[处理失败] eventId={}, duration={}ms, error={}",
            event.getEventId(), durationMs, errorMessage);
    }
}