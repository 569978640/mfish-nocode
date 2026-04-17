package cn.com.mfish.graph.sync.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 幂等表
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "幂等表请求参数")
public class ReqSyncIdempotentLog {
}
