package cn.com.mfish.graph.sync.model;

import lombok.Data;
import java.util.Map;

/**
 * CDC 事件模型
 */
@Data
public class CdcEvent {
    private String op;
    private String table;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Long ts;

    public String getOperationType() {
        if (op == null) {
            return "UNKNOWN";
        }
        return switch (op) {
            case "c" -> "INSERT";
            case "u" -> "UPDATE";
            case "d" -> "DELETE";
            case "r" -> "READ";
            default -> "UNKNOWN";
        };
    }
}
