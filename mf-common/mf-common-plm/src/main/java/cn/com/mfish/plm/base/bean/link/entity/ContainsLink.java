package cn.com.mfish.plm.base.bean.link.entity;

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
 * @description: 包含关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@TableName("contains_link")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "contains_link对象 包含关系")
public class ContainsLink extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("")
    @Schema(description = "")
	private String type;
    @ExcelProperty("")
    @Schema(description = "")
	private String fromId;
    @ExcelProperty("")
    @Schema(description = "")
	private String fromType;
    @ExcelProperty("")
    @Schema(description = "")
	private String toId;
    @ExcelProperty("")
    @Schema(description = "")
	private String toType;
}
