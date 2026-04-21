package cn.com.mfish.graph.sync.model;

import lombok.Data;
import java.util.Map;

/**
 * 图数据库点信息
 */
@Data
public class VertexInfo {
    private String id;
    private String tagName;
    private Map<String, Object> properties;

    public static VertexInfo of(String id, String tagName, Map<String, Object> properties) {
        VertexInfo vertex = new VertexInfo();
        vertex.setId(id);
        vertex.setTagName(tagName);
        vertex.setProperties(properties);
        return vertex;
    }
}
