package cn.com.mfish.graph.consumer;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.model.event.GraphSyncEvent;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 图同步MQ消费端
 * 监听RocketMQ消息，异步同步数据到NebulaGraph
 *
 * @author mfish
 * @date 2026-04-16
 */
@Slf4j
@Component
@RocketMQMessageListener(
    consumerGroup = "${rocketmq.consumer.group}",
    topic = "${rocketmq.consumer.topic}"
)
public class GraphSyncConsumer implements RocketMQListener<GraphSyncEvent> {

    @Autowired
    private NebulaClient nebulaClient;

    @Override
    public void onMessage(GraphSyncEvent event) {
        log.info("收到图同步事件: eventId={}, eventType={}", event.getEventId(), event.getEventType());

        try {
            switch (event.getEventType()) {
                case "CREATE":
                case "UPDATE":
                    processCreateOrUpdate(event);
                    break;
                case "DELETE":
                    processDelete(event);
                    break;
                default:
                    log.warn("未知事件类型: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("处理图同步事件失败: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    private void processCreateOrUpdate(GraphSyncEvent event) {
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            String tagName = getTagName(event);
            nebulaClient.insertVertex(tagName, event.getNodes());
            log.info("处理节点{}个, tagName={}", event.getNodes().size(), tagName);
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            String edgeName = getEdgeName(event);
            nebulaClient.insertEdge(edgeName, event.getEdges());
            log.info("处理边{}条, edgeName={}", event.getEdges().size(), edgeName);
        }
    }

    private void processDelete(GraphSyncEvent event) {
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            for (GraphNode node : event.getNodes()) {
                try {
                    nebulaClient.deleteVertex(node.getType(), node.getId());
                    log.info("删除节点: id={}, type={}", node.getId(), node.getType());
                } catch (Exception e) {
                    log.error("删除节点失败: id={}, type={}", node.getId(), node.getType(), e);
                }
            }
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            for (GraphEdge edge : event.getEdges()) {
                try {
                    nebulaClient.deleteEdge(edge.getType(), edge.getFromId(), edge.getToId());
                    log.info("删除边: fromId={}, toId={}, type={}", edge.getFromId(), edge.getToId(), edge.getType());
                } catch (Exception e) {
                    log.error("删除边失败: fromId={}, toId={}, type={}", edge.getFromId(), edge.getToId(), edge.getType(), e);
                }
            }
        }
    }

    private String getTagName(GraphSyncEvent event) {
        if (event.getNodes() != null && !event.getNodes().isEmpty()) {
            return event.getNodes().get(0).getType();
        }
        return "";
    }

    private String getEdgeName(GraphSyncEvent event) {
        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            return event.getEdges().get(0).getType();
        }
        return "";
    }
}