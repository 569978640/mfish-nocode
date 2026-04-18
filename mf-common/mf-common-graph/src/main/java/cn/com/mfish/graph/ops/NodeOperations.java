package cn.com.mfish.graph.ops;

import cn.com.mfish.graph.model.GraphNode;

import java.util.List;

/**
 * 节点操作接口
 * 定义节点的基本 CRUD 操作
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface NodeOperations {
    /**
     * 插入节点
     *
     * @param node 节点
     * @return true=成功
     */
    boolean insertNode(GraphNode node);

    /**
     * 插入或更新节点（幂等）
     *
     * @param node 节点
     * @return true=成功
     */
    boolean upsertNode(GraphNode node);

    /**
     * 更新节点
     *
     * @param node 节点
     * @return true=成功
     */
    boolean updateNode(GraphNode node);

    /**
     * 删除节点（物理删除）
     *
     * @param vid 节点 VID
     * @return true=成功
     */
    boolean deleteNode(String vid);

    /**
     * 软删除节点
     *
     * @param vid 节点 VID
     * @param deleteBy 删除人
     * @return true=成功
     */
    boolean softDeleteNode(String vid, String deleteBy);

    /**
     * 根据 VID 查询节点
     *
     * @param vid 节点 VID
     * @return 节点
     */
    GraphNode getNodeByVid(String vid);

    /**
     * 根据业务编码查询节点
     *
     * @param bizCode 业务编码
     * @return 节点
     */
    GraphNode getNodeByBizCode(String bizCode);

    /**
     * 批量插入节点
     *
     * @param nodes 节点列表
     * @return 成功数量
     */
    int batchInsertNodes(List<GraphNode> nodes);

    /**
     * 批量更新节点
     *
     * @param nodes 节点列表
     * @return 成功数量
     */
    int batchUpdateNodes(List<GraphNode> nodes);

    /**
     * 批量删除节点
     *
     * @param vids VID 列表
     * @return 成功数量
     */
    int batchDeleteNodes(List<String> vids);
}