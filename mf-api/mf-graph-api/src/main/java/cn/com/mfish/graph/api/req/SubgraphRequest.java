package cn.com.mfish.graph.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @description: 子图查询请求
 * @author: mfish
 * @date: 2026-04-16
 */
@Data
@Schema(name = "子图查询请求")
public class SubgraphRequest {
    @Schema(description = "起始节点ID")
    private String startId;

    @Schema(description = "起始节点类型")
    private String startType;

    @Schema(description = "深度")
    private Integer depth;

    @Schema(description = "边类型列表")
    private List<String> edgeTypes;
}