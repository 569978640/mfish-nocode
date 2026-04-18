package cn.com.mfish.graph.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 图谱边基础实体
 * 所有 PLM 业务边（关系）继承此类
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GraphEdge extends GraphBaseEntity {
    /**
     * 边类型（EdgeType 名称）
     */
    private String edgeType;

    /**
     * 源节点 VID
     */
    private String fromId;

    /**
     * 源节点类型
     */
    private String fromType;

    /**
     * 目标节点 VID
     */
    private String toId;

    /**
     * 目标节点类型
     */
    private String toType;

    /**
     * 关系类型（如 BOM/REFERENCES/USED_BY）
     */
    private String linkType;

    /**
     * 属性数据（JSON 格式）
     */
    private String propData;

    /**
     * 排名（用于多边场景，如多版本 BOM）
     */
    private Long rank;

    /**
     * 版本号（用于乐观锁）
     */
    private Integer version;

    /**
     * 设置边类型
     *
     * @param edgeType EdgeType 名称
     */
    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    /**
     * 获取边类型
     *
     * @return EdgeType 名称
     */
    public String getEdgeType() {
        return edgeType;
    }
}