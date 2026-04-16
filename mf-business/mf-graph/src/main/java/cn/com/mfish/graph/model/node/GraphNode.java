package cn.com.mfish.graph.model.node;

import lombok.Data;
import java.util.Date;

@Data
public class GraphNode {
    private String id;
    private String type;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}