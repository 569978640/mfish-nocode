package cn.com.mfish.graph.api.remote;

import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.constants.ServiceConstants;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.api.fallback.RemoteGraphQueryFallback;
import cn.com.mfish.graph.api.req.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * @description: 远程图查询服务
 * @author: mfish
 * @date: 2026-04-16
 */
@SuppressWarnings("rawtypes")
@FeignClient(contextId = "remoteGraphQueryService", value = ServiceConstants.GRAPH_SERVICE, fallbackFactory = RemoteGraphQueryFallback.class)
public interface RemoteGraphQueryApi {

    /**
     * 路径查询
     *
     * @param origin   请求来源，用于内部服务认证
     * @param request  查询请求
     * @return 查询结果
     */
    @PostMapping("/graph/query/paths")
    Result<Map<String, Object>> queryPaths(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody PathQueryRequest request);

    /**
     * 产品结构树查询
     *
     * @param origin   请求来源
     * @param request  产品树请求
     * @return 产品结构树
     */
    @PostMapping("/graph/query/product-tree")
    Result<Map<String, Object>> queryProductTree(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody ProductTreeRequest request);

    /**
     * 查询直接子节点
     *
     * @param origin    请求来源
     * @param productId 产品ID
     * @return 直接子节点列表
     */
    @PostMapping("/graph/query/direct-children")
    Result<Map<String, Object>> queryDirectChildren(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody Map<String, String> request);

    /**
     * 查询最短路径
     *
     * @param origin  请求来源
     * @param request 最短路径请求
     * @return 最短路径结果
     */
    @PostMapping("/graph/query/shortest-path")
    Result<Map<String, Object>> queryShortestPath(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody ShortestPathRequest request);

    /**
     * 查询邻居节点
     *
     * @param origin  请求来源
     * @param request 邻居查询请求
     * @return 邻居节点列表
     */
    @PostMapping("/graph/query/neighbors")
    Result<Map<String, Object>> queryNeighbors(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody NeighborsRequest request);

    /**
     * 查询子图
     *
     * @param origin  请求来源
     * @param request 子图查询请求
     * @return 子图结果
     */
    @PostMapping("/graph/query/subgraph")
    Result<Map<String, Object>> querySubgraph(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @RequestBody SubgraphRequest request);
}