package cn.com.mfish.plm.base.service;

import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.graph.model.node.GraphNode;

import java.util.List;

/**
 * PLM图同步客户端接口
 * 抽象图同步逻辑，不同实现可以支持不同的图数据库
 *
 * @author mfish
 * @date 2026-04-17
 */
public interface PlmGraphSyncClient {

    /**
     * 发送图同步事件（仅节点）
     *
     * @param entity 实体对象
     * @param nodeType 节点类型
     * @param eventType 事件类型
     * @param <T> 实体类型
     */
    <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType);

    /**
     * 批量发送图同步事件（仅节点）
     *
     * @param entities 实体列表
     * @param nodeType 节点类型
     * @param eventType 事件类型
     * @param <T> 实体类型
     */
    <T> void sendGraphSyncEventBatch(java.util.List<T> entities, String nodeType, String eventType);

    /**
     * 发送图同步事件（包含节点和边）
     * 用于一次性发送多个节点和边，保持一致性
     *
     * @param nodes 节点列表
     * @param edges 边列表
     * @param eventType 事件类型
     */
    void sendGraphSyncEventWithEdges(List<GraphNode> nodes, List<GraphEdge> edges, String eventType);

    /**
     * 发送边同步事件
     *
     * @param edge 边对象
     * @param eventType 事件类型
     */
    void sendGraphSyncEdgeEvent(GraphEdge edge, String eventType);

    /**
     * 批量发送边同步事件
     *
     * @param edges 边列表
     * @param eventType 事件类型
     */
    void sendGraphSyncEdgeEventBatch(List<GraphEdge> edges, String eventType);
}
