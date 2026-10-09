package cn.com.mfish.bfces.pbi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @description: PBI分类节点
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.0.0
 */
@Data
@Schema(description = "PBI分类节点对象")
public class PbiNode {
    
    @Schema(description = "图片地址")
    private String image;
    
    @Schema(description = "名称")
    private String name;
    
    @Schema(description = "显示名称")
    private String displayName;
    
    @Schema(description = "描述")
    private String description;
}
