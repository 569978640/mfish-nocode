package cn.com.mfish.graph.api.remote;

import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.constants.ServiceConstants;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.api.fallback.RemoteGraphSyncFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * @description: 远程图同步服务
 * @author: mfish
 * @date: 2026-04-16
 */
@SuppressWarnings("rawtypes")
@FeignClient(contextId = "remoteGraphSyncService", value = ServiceConstants.GRAPH_SERVICE, fallbackFactory = RemoteGraphSyncFallback.class)
public interface RemoteGraphSyncApi {

    /**
     * 触发全量同步
     *
     * @param origin 请求来源
     * @return 同步结果
     */
    @PostMapping("/graph/sync/full")
    Result<Map<String, Object>> fullSync(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin);

    /**
     * 同步所有节点
     *
     * @param origin 请求来源
     * @return 同步结果
     */
    @PostMapping("/graph/sync/nodes")
    Result<Map<String, Object>> syncNodes(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin);

    /**
     * 同步所有边
     *
     * @param origin 请求来源
     * @return 同步结果
     */
    @PostMapping("/graph/sync/edges")
    Result<Map<String, Object>> syncEdges(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin);

    /**
     * 获取同步状态
     *
     * @param origin 请求来源
     * @return 同步状态
     */
    @PostMapping("/graph/sync/status")
    Result<Map<String, Object>> getSyncStatus(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin);
}