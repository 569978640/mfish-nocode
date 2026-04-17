package cn.com.mfish.plm.base.bean.link.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 包含关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "包含关系请求参数")
public class ReqContainsLink {
}
