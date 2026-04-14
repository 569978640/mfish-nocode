package cn.com.plm.product.report.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: PLM产品报表
 * @author: mfish
 * @date: 2026-04-14
 * @version: V2.3.0
 */
@Data
@Accessors(chain = true)
@Schema(description = "PLM产品报表请求参数")
public class ReqProductReportFieldConfig {
    @Schema(description = "编号")
    private String number;
}
