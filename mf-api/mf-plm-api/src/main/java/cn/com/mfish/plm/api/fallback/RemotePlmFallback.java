package cn.com.mfish.plm.api.fallback;

import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.api.remote.RemotePlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * @description: 远程PLM服务失败处理
 * @author: mfish
 * @date: 2024/1/1
 */
@Slf4j
@Component
public class RemotePlmFallback implements FallbackFactory<RemotePlmService> {

    @Override
    public RemotePlmService create(Throwable cause) {
        log.error("错误:PLM服务调用异常", cause);
        return (origin, code) -> Result.fail("错误:PLM服务调用失败");
    }
}