package cn.com.mfish.gateway.filter;

import cn.com.mfish.common.captcha.common.CaptchaConstant;
import cn.com.mfish.common.captcha.service.CheckCodeService;
import cn.com.mfish.common.core.exception.CaptchaException;
import cn.com.mfish.gateway.common.GatewayUtils;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层验证码校验过滤器
 * <p>
 * 与 Nacos 路由配置 {@code - CheckCodeFilter} 配套使用：拦截 oauth2 等需要验证码的路由，
 * 从请求参数中读取 {@code captchaKey}（后端生成的 UUID）与 {@code captchaValue}（用户输入的验证码），
 * 调用 {@link CheckCodeService#checkCaptcha(String, String)} 进行校验，校验失败返回 401，校验通过放行。
 * <p>
 * 注意：默认仅校验当前路由命中后由业务方决定是否传入验证码；如需在某些子路径跳过校验，
 * 可在 yaml 中通过 {@code security.ignore.whites} 等白名单配置，或在业务服务自身处理验证码。
 *
 * @author: mfish
 * @date: 2026-09-09
 */
@Slf4j
@Component
public class CheckCodeFilter extends AbstractGatewayFilterFactory<CheckCodeFilter.Config> {

    @Resource
    private CheckCodeService checkCodeService;

    /**
     * 构造函数，初始化校验过滤器配置类
     */
    public CheckCodeFilter() {
        super(Config.class);
    }

    /**
     * 指定过滤器在路由配置中的注册名称，
     * 与 Nacos {@code mf-gateway-dev.yml} 中 {@code - CheckCodeFilter} 配置保持一致。
     *
     * @return 过滤器注册名称
     */
    @Override
    public String name() {
        return "CheckCodeFilter";
    }

    /**
     * 校验过滤器配置类，用于设置过滤器执行顺序
     */
    @Data
    public static class Config {
        /** 过滤器执行顺序 */
        private Integer order;
    }

    /**
     * 根据配置创建网关校验过滤器
     *
     * @param config 过滤器配置
     * @return 网关过滤器实例
     */
    @Override
    public @NonNull GatewayFilter apply(Config config) {
        CheckCodeGatewayFilter checkCodeGatewayFilter = new CheckCodeGatewayFilter();
        Integer order = config == null ? null : config.getOrder();
        if (order == null) {
            return checkCodeGatewayFilter;
        }
        return new org.springframework.cloud.gateway.filter.OrderedGatewayFilter(checkCodeGatewayFilter, order);
    }

    /**
     * 实际执行验证码校验逻辑的网关过滤器
     */
    public class CheckCodeGatewayFilter implements GatewayFilter {
        /**
         * 拦截请求并校验验证码：缺失/错误/失效则返回 401，校验通过放行到下游服务
         *
         * @param exchange 服务端 Web 交换对象
         * @param chain    网关过滤器链
         * @return 过滤结果
         */
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            // 优先从 query 参数读取，兼容 GET 请求；
            // POST 表单读取需要订阅 body，会破坏流式处理，因此网关层仅校验 query 中传入的验证码
            String value = request.getQueryParams().getFirst(CaptchaConstant.CAPTCHA_VALUE);
            String key = request.getQueryParams().getFirst(CaptchaConstant.CAPTCHA_KEY);
            try {
                checkCodeService.checkCaptcha(value, key);
            } catch (CaptchaException ex) {
                log.warn("[验证码校验失败]路径:{},原因:{}", request.getPath(), ex.getMessage());
                return GatewayUtils.webFluxResponseWriter(exchange.getResponse(), HttpStatus.UNAUTHORIZED, ex.getMessage());
            } catch (Exception ex) {
                log.error("[验证码校验异常]路径:{}", request.getPath(), ex);
                return GatewayUtils.webFluxResponseWriter(exchange.getResponse(), HttpStatus.UNAUTHORIZED, ex.getMessage());
            }
            return chain.filter(exchange);
        }
    }
}
