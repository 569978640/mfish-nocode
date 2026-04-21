package cn.com.mfish.graph.sync.graph;

import org.apache.ibatis.annotations.Param;

/**
 * 图数据库操作 DAO（NgBatis 风格）
 */
public interface GraphOperationDao {

    /**
     * UPSERT 顶点
     */
    void upsertVertex(@Param("tagName") String tagName, @Param("id") String id, @Param("props") String props);

    /**
     * UPSERT 边
     */
    void upsertEdge(@Param("edgeName") String edgeName, @Param("fromId") String fromId,
                    @Param("toId") String toId, @Param("props") String props);

    /**
     * 删除顶点
     */
    void deleteVertex(@Param("tagName") String tagName, @Param("id") String id);

    /**
     * 删除边
     */
    void deleteEdge(@Param("edgeName") String edgeName, @Param("fromId") String fromId, @Param("toId") String toId);

    /**
     * 检查顶点是否存在
     */
    Integer vertexExists(@Param("tagName") String tagName, @Param("id") String id);

    /**
     * 检查边是否存在
     */
    Integer edgeExists(@Param("edgeName") String edgeName, @Param("fromId") String fromId, @Param("toId") String toId);
}
