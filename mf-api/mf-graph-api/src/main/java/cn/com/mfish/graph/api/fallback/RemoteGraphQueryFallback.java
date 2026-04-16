package cn.com.mfish.graph.api.fallback;

import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.api.remote.RemoteGraphQueryApi;
import cn.com.mfish.graph.api.req.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @description: 远程图查询服务失败处理
 * @author: mfish
 * @date: 2026-04-16
 */
@Slf4j
@Component
public class RemoteGraphQueryFallback implements FallbackFactory<RemoteGraphQueryApi> {

    @Override
    public RemoteGraphQueryApi create(Throwable cause) {
        log.error("图查询服务调用异常", cause);
        return new RemoteGraphQueryApi() {
            @Override
            public Result<Map<String, Object>> queryPaths(String origin, PathQueryRequest request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> queryProductTree(String origin, ProductTreeRequest request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> queryDirectChildren(String origin, Map<String, String> request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> queryShortestPath(String origin, ShortestPathRequest request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> queryNeighbors(String origin, NeighborsRequest request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> querySubgraph(String origin, SubgraphRequest request) {
                return Result.fail("图查询服务调用失败: " + cause.getMessage());
            }
        };
    }
}