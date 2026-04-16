package cn.com.mfish.graph.model.edge;

import lombok.Data;
import java.util.Date;
import java.util.Map;

@Data
public class GraphEdge {
    private String id;
    private String type;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String fromId;
    private String fromType;
    private String toId;
    private String toType;
    private Map<String, Object> properties;
}