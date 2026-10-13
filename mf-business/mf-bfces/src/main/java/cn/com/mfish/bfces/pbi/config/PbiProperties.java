package cn.com.mfish.bfces.pbi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @description: PBI（Windchill）接口配置属性，对应配置前缀 pbi.api
 * @author: mfish
 * @date: 2026-10-13
 * @version: V1.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "pbi.api")
public class PbiProperties {

    /**
     * 接口域名地址，必须包含协议，如 https://plm13test.bfces.com
     */
    private String domain = "https://plm13test.bfces.com";;

    /**
     * 接口鉴权令牌，原样写入 Authorization 请求头
     */
    private String authorization;

    /**
     * 部件图片在应用服务器上的本地存放目录（按部件编号命名PNG文件）。
     * 需与Windchill静态资源目录保持一致，默认取测试环境路径，生产环境通过配置覆盖。
     */
    private String imageDir = "/plm/ptc/Windchill_13.1/Windchill/codebase/wtcore/test/part/image/";

    /**
     * 部件Windchill信息页路径（不含域名，与 {@link #domain} 拼接后再追加OID）。
     * 默认取测试环境路径，生产环境通过 pbi.api.info-page-path 配置覆盖；
     * 在 yml 中配置时值含“#”，请用引号包裹。
     */
    private String infoPagePath = "/Windchill/app/#ptc1/tcomp/infoPage?oid=";

    /**
     * Windchill信息页Basic认证的固定密码（各账号共用同一密码）。
     * 账号取当前登录用户账号，密码默认测试环境值，生产环境务必通过
     * Nacos配置 pbi.api.basic-password 覆盖，不要把真实密码硬编码在代码中。
     */
    private String basicPassword = "Bfces@2021";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 10000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 30000;
}
