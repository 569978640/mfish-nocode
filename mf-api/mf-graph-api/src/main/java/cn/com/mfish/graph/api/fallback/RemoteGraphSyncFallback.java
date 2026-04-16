package cn.com.mfish.graph.api.fallback;

import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.api.remote.RemoteGraphSyncApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @description: 远程图同步服务失败处理
 * @author: mfish
 * @date: 2026-04-16
 */
@Slf4j
@Component
public class RemoteGraphSyncFallback implements FallbackFactory<RemoteGraphSyncApi> {

    @Override
    public RemoteGraphSyncApi create(Throwable cause) {
        log.error("图同步服务调用异常", cause);
        return new RemoteGraphSyncApi() {
            @Override
            public Result<Map<String, Object>> fullSync(String origin) {
                return Result.fail("图同步服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> syncNodes(String origin) {
                return Result.fail("图同步服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> syncEdges(String origin) {
                return Result.fail("图同步服务调用失败: " + cause.getMessage());
            }

            @Override
            public Result<Map<String, Object>> getSyncStatus(String origin) {
                return Result.fail("图同步服务调用失败: " + cause.getMessage());
            }
        };
    }
}