package cn.com.mfish.plm.base.bean.container.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 产品库
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "产品库请求参数")
public class ReqProduct {
    @Schema(description = "产品ID")
    private String id;
    @Schema(description = "产品名称")
    private String name;
    @Schema(description = "")
    private String create_by;
}
