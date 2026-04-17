package cn.com.mfish.graph.sync.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @description: 图同步操作日志
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Data
@Accessors(chain = true)
@Schema(description = "图同步操作日志请求参数")
public class ReqSyncOperationLog {
}
