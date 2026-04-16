package cn.com.mfish.graph.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @description: 路径查询请求
 * @author: mfish
 * @date: 2026-04-16
 */
@Data
@Schema(name = "路径查询请求")
public class PathQueryRequest {
    @Schema(description = "起始节点ID")
    private String startId;

    @Schema(description = "起始节点类型")
    private String startType;

    @Schema(description = "边类型列表")
    private List<String> edgeTypes;

    @Schema(description = "方向: OUT/IN/BOTH")
    private String direction;

    @Schema(description = "查询深度，默认5层")
    private Integer depth;
}