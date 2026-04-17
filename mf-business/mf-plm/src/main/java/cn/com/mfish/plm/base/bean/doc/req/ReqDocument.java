package cn.com.mfish.plm.base.bean.doc.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 文档小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "文档小版本请求参数")
public class ReqDocument {
    @Schema(description = "")
    private String version;
    @Schema(description = "")
    private String state;
}
