package cn.com.mfish.plm.base.service.impl;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.plm.base.service.PlmGraphSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PLM图同步客户端实现
 * 使用RocketMQ发送图同步消息
 *
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
@Component
public class PlmGraphSyncClientImpl implements PlmGraphSyncClient {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.topic:plm-graph-sync}")
    private String topic;

    @Override
    public <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType) {
        if (entity == null) {
            return;
        }
        GraphNode node = convertToGraphNode(entity, nodeType);
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventType(eventType);
        event.setNodes(List.of(node));
        sendEvent(event);
    }

    @Override
    public <T> void sendGraphSyncEventBatch(List<T> entities, String nodeType, String eventType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        List<GraphNode> nodes = new ArrayList<>();
        for (T entity : entities) {
            nodes.add(convertToGraphNode(entity, nodeType));
        }
        GraphSyncEvent event = new GraphSyncEvent();
        event.setEventType(eventType);
        event.setNodes(nodes);
        sendEvent(event);
        log.info("批量发送图同步事件: nodeType={}, eventType={}, count={}", nodeType, eventType, nodes.size());
    }

    private void sendEvent(GraphSyncEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        if (event.getSource() == null) {
            event.setSource("mf-plm");
        }
        try {
            rocketMQTemplate.asyncSend(topic, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("图同步事件发送成功, eventId={}, result={}", event.getEventId(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("图同步事件发送失败, eventId={}", event.getEventId(), e);
                }
            });
        } catch (Exception e) {
            log.error("发送图同步事件异常, eventId={}", event.getEventId(), e);
        }
    }

    private <T> GraphNode convertToGraphNode(T entity, String nodeType) {
        GraphNode node = new GraphNode();
        BeanUtils.copyProperties(entity, node);
        node.setType(nodeType);
        return node;
    }
}
