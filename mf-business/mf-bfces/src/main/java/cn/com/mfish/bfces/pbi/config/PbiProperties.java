package cn.com.mfish.bfces.pbi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @description: PBI（Windchill）接口配置属性，对应配置前缀 pbi.api
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.2.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "pbi.api")
public class PbiProperties {

    /**
     * 接口域名地址，必须包含协议，如 https://plm13test.bfces.com
     */
    private String domain;

    /**
     * 接口鉴权令牌，原样写入 Authorization 请求头
     */
    private String authorization;

    /**
     * 部件图片静态访问URL的路径前缀（不含域名），与Windchill codebase下的图片目录映射一致，
     * 拼接方式：domain + imagePathPrefix + 部件编号 + .png
     */
    private String imagePathPrefix = "/Windchill/wtcore/test/part/image/";

    /**
     * 部件Windchill信息页路径（不含域名，与 {@link #domain} 拼接后再追加OID）。
     * 在 yml 中配置时值含“#”，请用引号包裹。
     */
    private String infoPagePath = "/Windchill/app/#ptc1/tcomp/infoPage?oid=";

    /**
     * Windchill信息页Basic认证的固定密码（各账号共用同一密码）。
     * 账号取当前登录用户账号，密码不提供默认值，必须通过 Nacos 配置 pbi.api.basic-password 注入；
     * 未配置时部件信息页链接降级为不可点击，避免把无效链接暴露给前端。
     */
    private String basicPassword;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 30000;
}
