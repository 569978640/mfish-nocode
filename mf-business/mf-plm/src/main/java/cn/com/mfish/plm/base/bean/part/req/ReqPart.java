package cn.com.mfish.plm.base.bean.part.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 部件小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "部件小版本请求参数")
public class ReqPart {
    @Schema(description = "")
    private String state;
}
