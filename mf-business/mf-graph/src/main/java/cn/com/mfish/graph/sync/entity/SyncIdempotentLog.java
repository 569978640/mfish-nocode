package cn.com.mfish.graph.sync.entity;

import cn.com.mfish.common.core.entity.BaseEntity;
import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @description: 幂等表
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Data
@TableName("sync_idempotent_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "sync_idempotent_log对象 幂等表")
public class SyncIdempotentLog extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("事件唯一ID")
    @Schema(description = "事件唯一ID")
	private String eventId;
    @ExcelProperty("事件类型：CREATE/UPDATE/DELETE")
    @Schema(description = "事件类型：CREATE/UPDATE/DELETE")
	private String eventType;
    @ExcelProperty("节点数量")
    @Schema(description = "节点数量")
	private Long nodeCount;
    @ExcelProperty("边数量")
    @Schema(description = "边数量")
	private Long edgeCount;
    @ExcelProperty("状态：PROCESSING/COMPLETED/FAILED")
    @Schema(description = "状态：PROCESSING/COMPLETED/FAILED")
	private String status;
    @ExcelProperty("重试次数")
    @Schema(description = "重试次数")
	private Long retryCount;
    @ExcelProperty("错误信息")
    @Schema(description = "错误信息")
	private String errorMessage;
}
