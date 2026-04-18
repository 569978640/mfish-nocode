package cn.com.mfish.graph.sync.consumer;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.service.IdempotentService;
import cn.com.mfish.graph.sync.service.NebulaWriteService;
import cn.com.mfish.graph.sync.service.SyncLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 图同步消息消费者
 * 监听 RocketMQ 消息，实现图数据库同步
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${rocketmq.consumer.group:plm-graph-sync-group}",
    topic = "${rocketmq.consumer.topic:plm-graph-sync}"
)
public class GraphSyncConsumer implements RocketMQListener<GraphSyncEvent> {

    @Autowired
    private IdempotentService idempotentService;

    @Autowired
    private NebulaWriteService nebulaWriteService;

    @Autowired
    private SyncLogService syncLogService;

    @Override
    public void onMessage(GraphSyncEvent event) {
        if (event == null || event.getEventId() == null) {
            log.warn("收到无效消息，event 或 eventId 为空");
            return;
        }

        String eventId = event.getEventId();
        long startTime = System.currentTimeMillis();

        log.info("[MQ接收] eventId={}, eventType={}, nodes={}, edges={}",
            eventId, event.getEventType(),
            event.getNodes() == null ? 0 : event.getNodes().size(),
            event.getEdges() == null ? 0 : event.getEdges().size());

        SyncIdempotentLog idempotentLog = idempotentService.checkAndCreate(event);
        if (idempotentLog == null) {
            log.info("[MQ跳过] 事件已处理过, eventId={}", eventId);
            syncLogService.logSkip(event, "幂等检查跳过");
            return;
        }

        syncLogService.logStart(event);
        int retryCount = idempotentLog.getRetryCount() != null ? idempotentLog.getRetryCount().intValue() : 0;

        try {
            switch (event.getEventType()) {
                case "CREATE":
                case "UPDATE":
                    nebulaWriteService.upsertNodesAndEdges(event);
                    break;
                case "DELETE":
                    nebulaWriteService.deleteNodesAndEdges(event);
                    break;
                default:
                    throw new IllegalArgumentException("未知事件类型: " + event.getEventType());
            }

            idempotentService.markCompleted(eventId);

            long duration = System.currentTimeMillis() - startTime;
            syncLogService.logSuccess(event, duration);

            log.info("[处理完成] eventId={}, duration={}ms", eventId, duration);

        } catch (Exception e) {
            log.error("[处理失败] eventId={}, error={}", eventId, e.getMessage(), e);

            idempotentService.markFailed(eventId, e.getMessage(), retryCount);

            long duration = System.currentTimeMillis() - startTime;
            syncLogService.logFailed(event, duration, e.getMessage());

            throw new RuntimeException("图同步处理失败: " + eventId, e);
        }
    }
}