package cn.com.mfish.bfces.pbi.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @description: PBI特征参数展示配置
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.1.0
 */
@Data
@TableName("PBI_FeatureParameterDisplayConfig")
@Schema(description = "PBI特征参数展示配置对象")
public class PbiParamConfig {

    @Schema(description = "一级分类")
    private String firstCategory;

    @Schema(description = "属性分组")
    private String paramGroup;

    @Schema(description = "参数编码")
    private String param;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "Windchill内部名称")
    private String internalName;

    @Schema(description = "部件视图列名")
    private String columnName;

    @Schema(description = "分组颜色")
    private String groupColor;

    @Schema(description = "参数颜色")
    private String paramColor;
}
