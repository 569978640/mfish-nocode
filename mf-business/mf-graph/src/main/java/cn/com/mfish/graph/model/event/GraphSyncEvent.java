package cn.com.mfish.graph.model.event;

import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.Data;
import java.util.List;

@Data
public class GraphSyncEvent {
    private String eventId;
    private String eventType;
    private Long timestamp;
    private String source;
    private String operator;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}