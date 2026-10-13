package cn.com.mfish.bfces.pbi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @description: PBI筛选属性
 * @author: mfish
 * @date: 2026-10-12
 * @version: V1.0.0
 */
@Data
@Schema(description = "PBI筛选属性对象")
public class PbiFilterProperty {

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "属性值列表")
    private List<String> values;

    @Schema(description = "属性名称")
    private String name;

    @Schema(description = "类型")
    private String type;
}
