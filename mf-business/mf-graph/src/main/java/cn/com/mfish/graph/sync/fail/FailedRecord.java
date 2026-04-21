package cn.com.mfish.graph.sync.fail;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 同步失败记录
 */
@Data
@TableName("sync_failed_records")
public class FailedRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String tableName;
    private String operationType;
    private String payload;
    private String errorMessage;
    private Integer retryCount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime processTime;
}
