package cn.com.mfish.plm.base.bean.folder.entity;

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
 * @description: 文件夹
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@TableName("folder")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "folder对象 文件夹")
public class Folder extends BaseEntity<String> {
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
	private String name;
}
