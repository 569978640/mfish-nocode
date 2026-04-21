package cn.com.mfish.graph.sync.model;

import lombok.Data;
import java.util.Map;

/**
 * 图数据库边信息
 */
@Data
public class EdgeInfo {
    private String id;
    private String edgeName;
    private String fromId;
    private String fromType;
    private String toId;
    private String toType;
    private Map<String, Object> properties;

    public static EdgeInfo of(String id, String edgeName, String fromId, String fromType,
                              String toId, String toType, Map<String, Object> properties) {
        EdgeInfo edge = new EdgeInfo();
        edge.setId(id);
        edge.setEdgeName(edgeName);
        edge.setFromId(fromId);
        edge.setFromType(fromType);
        edge.setToId(toId);
        edge.setToType(toType);
        edge.setProperties(properties);
        return edge;
    }
}
