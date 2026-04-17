package cn.com.mfish.graph.model.event;

import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.Data;
import java.util.List;

/**
 * 图同步事件
 * 用于RocketMQ消息传递PLM数据变更
 *
 * @author mfish
 * @date 2026-04-17
 */
@Data
public class GraphSyncEvent {
    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 事件类型：CREATE/UPDATE/DELETE
     */
    private String eventType;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 事件来源模块
     */
    private String source;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 节点列表
     */
    private List<GraphNode> nodes;

    /**
     * 边列表
     */
    private List<GraphEdge> edges;
}