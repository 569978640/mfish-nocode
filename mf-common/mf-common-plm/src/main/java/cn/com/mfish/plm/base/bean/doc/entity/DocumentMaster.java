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
 * 文档主数据实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_master")
public class DocumentMaster extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("类型")
    @Schema(description = "类型")
    private String type;
    private String number;
    private String name;
}