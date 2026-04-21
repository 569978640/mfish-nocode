package cn.com.mfish.graph.sync.consumer;

import cn.com.mfish.graph.sync.service.incremental.IncrementalSyncHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 同步消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "${rocketmq.consumer.topic:plm-graph-sync}",
    consumerGroup = "${rocketmq.consumer.group:plm-graph-sync-group}"
)
public class SyncMessageConsumer implements RocketMQListener<String> {
    private final IncrementalSyncHandler incrementalSyncHandler;

    @Override
    public void onMessage(String message) {
        log.debug("收到同步消息: {}", message);
        incrementalSyncHandler.handleMessage(message);
    }
}
