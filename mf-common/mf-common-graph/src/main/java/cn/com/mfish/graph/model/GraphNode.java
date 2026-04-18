package cn.com.mfish.graph.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图谱节点
 * 所有 PLM 业务节点（产品/零部件/文档等）继承此类
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GraphNode extends GraphBaseEntity {
    /**
     * 节点类型（Tag 名称）
     */
    private String nodeType;

    /**
     * 业务编码（如产品编号、零部件编号）
     */
    private String bizCode;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 扩展属性（JSON 格式存储动态字段）
     */
    private String extData;

    /**
     * 版本号（用于乐观锁）
     */
    private Integer version;

    /**
     * 设置节点类型
     *
     * @param nodeType Tag 名称
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * 获取节点类型
     *
     * @return Tag 名称
     */
    public String getNodeType() {
        return nodeType;
    }
}