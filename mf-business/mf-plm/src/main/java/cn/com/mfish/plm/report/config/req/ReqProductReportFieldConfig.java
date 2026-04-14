package cn.com.mfish.plm.report.config.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 产品系列报表字段配置
 * @author: mfish
 * @date: 2026-04-15
 * @version: V2.3.0
 */
@Data
@Accessors(chain = true)
@Schema(description = "产品系列报表字段配置请求参数")
public class ReqProductReportFieldConfig {
    @Schema(description = "编号")
    private String number;
}
