package cn.com.mfish.graph.sync.graph;

import cn.com.mfish.graph.sync.model.*;
import java.util.List;

/**
 * 图数据库操作服务接口
 */
public interface GraphOperationService {
    /**
     * UPSERT 顶点
     */
    void upsertVertex(VertexInfo vertex);

    /**
     * UPSERT 边
     */
    void upsertEdge(EdgeInfo edge);

    /**
     * 删除顶点
     */
    void deleteVertex(String tagName, String id);

    /**
     * 删除边
     */
    void deleteEdge(String edgeName, String fromId, String toId);

    /**
     * 批量 UPSERT 顶点
     */
    void batchUpsertVertex(List<VertexInfo> vertices);

    /**
     * 批量 UPSERT 边
     */
    void batchUpsertEdge(List<EdgeInfo> edges);

    /**
     * 检查顶点是否存在
     */
    boolean vertexExists(String tagName, String id);

    /**
     * 检查边是否存在
     */
    boolean edgeExists(String edgeName, String fromId, String toId);
}
