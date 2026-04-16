package cn.com.mfish.graph.api.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @description: 产品结构树查询请求
 * @author: mfish
 * @date: 2026-04-16
 */
@Data
@Schema(name = "产品结构树查询请求")
public class ProductTreeRequest {
    @Schema(description = "产品ID")
    private String productId;

    @Schema(description = "查询深度，默认5层")
    private Integer depth;
}