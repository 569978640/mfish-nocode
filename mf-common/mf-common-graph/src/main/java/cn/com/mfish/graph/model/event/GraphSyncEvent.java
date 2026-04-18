package cn.com.mfish.graph.model.event;

import cn.com.mfish.graph.model.GraphEdge;
import cn.com.mfish.graph.model.GraphNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 图数据库同步事件
 * 用于PLM模块与Graph模块之间的消息传递
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
@Schema(description = "图数据库同步事件")
public class GraphSyncEvent {
    @Schema(description = "事件唯一ID（幂等键）")
    private String eventId;

    @Schema(description = "事件类型：CREATE/UPDATE/DELETE")
    private String eventType;

    @Schema(description = "事件时间戳")
    private Long timestamp;

    @Schema(description = "来源模块：mf-plm")
    private String source;

    @Schema(description = "操作人")
    private String operator;

    @Schema(description = "节点列表")
    private List<GraphNode> nodes;

    @Schema(description = "边列表")
    private List<GraphEdge> edges;
}