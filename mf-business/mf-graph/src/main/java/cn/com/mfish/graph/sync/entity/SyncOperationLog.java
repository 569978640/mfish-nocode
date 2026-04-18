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
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.Date;

/**
 * @description: 图同步操作日志
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Data
@TableName("sync_operation_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "sync_operation_log对象 图同步操作日志")
public class SyncOperationLog extends BaseEntity<Long> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.AUTO)
    @Accessors(chain = true)
    private Long id;
    @ExcelProperty("事件唯一ID")
    @Schema(description = "事件唯一ID")
	private String eventId;
    @ExcelProperty("操作类型：PREPARE/PROCESS/COMPLETE/FAILED")
    @Schema(description = "操作类型：PREPARE/PROCESS/COMPLETE/FAILED")
	private String operation;
    @ExcelProperty("节点数量")
    @Schema(description = "节点数量")
	private Long nodeCount;
    @ExcelProperty("边数量")
    @Schema(description = "边数量")
	private Long edgeCount;
    @ExcelProperty("开始时间")
    @Schema(description = "开始时间")
	private Date startTime;
    @ExcelProperty("结束时间")
    @Schema(description = "结束时间")
	private Date endTime;
    @ExcelProperty("耗时(毫秒)")
    @Schema(description = "耗时(毫秒)")
	private Long durationMs;
    @ExcelProperty("状态：SUCCESS/FAILED")
    @Schema(description = "状态：SUCCESS/FAILED")
	private String status;
    @ExcelProperty("错误信息")
    @Schema(description = "错误信息")
	private String errorMessage;
    @ExcelProperty("详细信息")
    @Schema(description = "详细信息")
	private String detail;
}
