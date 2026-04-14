package cn.com.mfish.plm.api.remote;

import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.constants.ServiceConstants;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.api.fallback.RemotePlmFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * @description: 远程PLM服务
 * @author: mfish
 * @date: 2024/1/1
 */
@SuppressWarnings("rawtypes")
@FeignClient(contextId = "remotePlmService", value = ServiceConstants.PLM_SERVICE, fallbackFactory = RemotePlmFallback.class)
public interface RemotePlmService {
    /**
     * 根据编号获取PLM数据
     *
     * @param origin 请求来源，用于内部服务认证
     * @param code PLM数据编号
     * @return PLM数据信息
     */
    @GetMapping("/plm/data/{code}")
    Result getDataByCode(@RequestHeader(RPCConstants.REQ_ORIGIN) String origin, @PathVariable("code") String code);
}