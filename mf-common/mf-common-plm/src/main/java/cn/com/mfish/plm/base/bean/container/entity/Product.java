package cn.com.mfish.plm.base.bean.container.entity;

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
 * @description: 产品库
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@TableName("product")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "product对象 产品库")
public class Product extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("类型")
    @Schema(description = "类型")
    private String type;
    @ExcelProperty("产品名称")
    @Schema(description = "产品名称")
    private String name;
}
