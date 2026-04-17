package cn.com.mfish.plm.base.bean.doc.entity;

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
 * @description: 文档主数据
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@TableName("document_master")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "document_master对象 文档主数据")
public class DocumentMaster extends BaseEntity<String> {
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
	private String number;
    @ExcelProperty("")
    @Schema(description = "")
	private String name;
}
