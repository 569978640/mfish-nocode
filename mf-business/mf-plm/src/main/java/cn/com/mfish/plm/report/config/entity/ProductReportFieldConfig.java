package cn.com.mfish.plm.report.config.entity;

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
 * @description: 产品系列报表字段配置
 * @author: mfish
 * @date: 2026-04-15
 * @version: V2.3.0
 */
@Data
@TableName("PRODUCTREPORTFIELDCONFIG")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "PRODUCTREPORTFIELDCONFIG对象 产品系列报表字段配置")
public class ProductReportFieldConfig extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("编号")
    @Schema(description = "编号")
	private String number;
    @ExcelProperty("字段")
    @Schema(description = "字段")
	private String field;
    @ExcelProperty("类型")
    @Schema(description = "类型")
	private String type;
}
