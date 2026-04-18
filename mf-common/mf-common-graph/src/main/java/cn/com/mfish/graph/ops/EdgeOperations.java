package cn.com.mfish.graph.ops;

import cn.com.mfish.graph.model.GraphEdge;

import java.util.List;

/**
 * 边操作接口
 * 定义边的基本 CRUD 操作
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface EdgeOperations {
    /**
     * 插入边
     *
     * @param edge 边
     * @return true=成功
     */
    boolean insertEdge(GraphEdge edge);

    /**
     * 插入或更新边（幂等）
     *
     * @param edge 边
     * @return true=成功
     */
    boolean upsertEdge(GraphEdge edge);

    /**
     * 更新边
     *
     * @param edge 边
     * @return true=成功
     */
    boolean updateEdge(GraphEdge edge);

    /**
     * 删除边
     *
     * @param fromId 源节点 VID
     * @param edgeType 边类型
     * @param toId 目标节点 VID
     * @return true=成功
     */
    boolean deleteEdge(String fromId, String edgeType, String toId);

    /**
     * 软删除边
     *
     * @param fromId 源节点 VID
     * @param edgeType 边类型
     * @param toId 目标节点 VID
     * @param deleteBy 删除人
     * @return true=成功
     */
    boolean softDeleteEdge(String fromId, String edgeType, String toId, String deleteBy);

    /**
     * 查询边的属性
     *
     * @param fromId 源节点 VID
     * @param edgeType 边类型
     * @param toId 目标节点 VID
     * @return 边
     */
    GraphEdge getEdge(String fromId, String edgeType, String toId);

    /**
     * 查询源节点的所有出边
     *
     * @param fromId 源节点 VID
     * @return 边列表
     */
    List<GraphEdge> getOutEdges(String fromId);

    /**
     * 查询目标节点的所有入边
     *
     * @param toId 目标节点 VID
     * @return 边列表
     */
    List<GraphEdge> getInEdges(String toId);

    /**
     * 批量插入边
     *
     * @param edges 边列表
     * @return 成功数量
     */
    int batchInsertEdges(List<GraphEdge> edges);

    /**
     * 批量更新边
     *
     * @param edges 边列表
     * @return 成功数量
     */
    int batchUpdateEdges(List<GraphEdge> edges);

    /**
     * 批量删除边
     *
     * @param fromIds 源节点 VID 列表
     * @param edgeType 边类型
     * @param toIds 目标节点 VID 列表
     * @return 成功数量
     */
    int batchDeleteEdges(List<String> fromIds, String edgeType, List<String> toIds);
}