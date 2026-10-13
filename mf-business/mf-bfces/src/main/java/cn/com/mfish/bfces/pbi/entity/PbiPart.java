package cn.com.mfish.bfces.pbi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * @description: PBI产品型号（部件）
 * @author: mfish
 * @date: 2026-10-12
 * @version: V1.1.0
 */
@Data
@Schema(description = "PBI产品型号对象")
public class PbiPart {

    @Schema(description = "部件编号")
    private String wtPartNumber;

    @Schema(description = "部件名称")
    private String name;

    @Schema(description = "部件图片地址")
    private String image;

    @Schema(description = "部件Windchill信息页地址（已含当前账号Basic认证凭据，供浏览器新标签页直接打开）")
    private String pageUrl;

    @Schema(description = "部件扩展属性，key为视图列名")
    private Map<String, Object> attrs;
}
