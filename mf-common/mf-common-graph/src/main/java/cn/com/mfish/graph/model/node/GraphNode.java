package cn.com.mfish.graph.model.node;

import cn.com.mfish.common.core.entity.BaseEntity;
import lombok.Data;
import java.util.Date;

@Data
public class GraphNode extends BaseEntity<String> {
    private String type;
}