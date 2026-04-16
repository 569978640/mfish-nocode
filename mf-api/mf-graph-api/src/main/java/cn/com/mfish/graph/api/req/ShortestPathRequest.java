package cn.com.mfish.graph.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @description: 最短路径查询请求
 * @author: mfish
 * @date: 2026-04-16
 */
@Data
@Schema(name = "最短路径查询请求")
public class ShortestPathRequest {
    @Schema(description = "起始节点ID")
    private String startId;

    @Schema(description = "起始节点类型")
    private String startType;

    @Schema(description = "目标节点ID")
    private String endId;

    @Schema(description = "目标节点类型")
    private String endType;
}