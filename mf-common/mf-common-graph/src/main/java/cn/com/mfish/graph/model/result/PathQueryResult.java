package cn.com.mfish.graph.model.result;

import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.node.GraphNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路径查询结果
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@Schema(description = "路径查询结果")
public class PathQueryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "节点列表")
    private List<GraphNode> nodes;

    @Schema(description = "边列表")
    private List<GraphEdge> edges;

    public PathQueryResult() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public static PathQueryResult fail(String errorMessage) {
        PathQueryResult result = new PathQueryResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static PathQueryResult ok(List<GraphNode> nodes, List<GraphEdge> edges) {
        PathQueryResult result = new PathQueryResult();
        result.setSuccess(true);
        result.setNodes(nodes);
        result.setEdges(edges);
        return result;
    }

    public Set<String> getAllNodeIds() {
        return nodes.stream()
                .map(GraphNode::getId)
                .collect(Collectors.toSet());
    }
}
