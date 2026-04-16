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
            nebulaClient.insertVertex(getTagName(event), event.getNodes());
        }

        if (event.getEdges() != null && !event.getEdges().isEmpty()) {
            nebulaClient.insertEdge(getEdgeName(event), event.getEdges());
        }
    }

    private void processDelete(GraphSyncEvent event) {
        // 处理删除逻辑
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
