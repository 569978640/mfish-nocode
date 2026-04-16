package cn.com.mfish.graph.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @description: 邻居节点查询请求
 * @author: mfish
 * @date: 2026-04-16
 */
@Data
@Schema(name = "邻居节点查询请求")
public class NeighborsRequest {
    @Schema(description = "节点ID")
    private String nodeId;

    @Schema(description = "节点类型")
    private String nodeType;

    @Schema(description = "边类型列表")
    private List<String> edgeTypes;

    @Schema(description = "方向: OUT/IN/BOTH")
    private String direction;
}