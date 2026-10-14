package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI产品型号转换器。
 * 负责将零件视图查询行转换为 {@link PbiPart}（或下拉选项文本），
 * 统一处理固定字段提取、扩展属性归集、图片地址与Windchill信息页地址拼接。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiPartConverter {

    /** 部件图片文件后缀 */
    private static final String IMAGE_SUFFIX = ".png";

    /** 已单独提取、不放入扩展属性attrs的固定字段（大写） */
    private static final Set<String> FIXED_FIELDS = Set.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID");

    private final PbiProperties pbiProperties;

    /**
     * 将视图查询行转换为产品型号对象，提取固定字段并解析图片地址与信息页地址，其余列放入扩展属性
     *
     * @param row 视图查询行（key为大写列名）
     * @return 产品型号对象
     */
    public PbiPart toPart(Map<String, Object> row) {
        PbiPart part = new PbiPart();
        String wtPartNumber = getString(row, "WTPARTNUMBER");
        part.setWtPartNumber(wtPartNumber);
        part.setName(getString(row, "NAME"));
        // 编号合法即生成图片静态地址，图片是否存在由前端加载失败时回退默认图
        part.setImage(resolveImage(wtPartNumber));
        // Windchill信息页地址：配置域名 + 信息页路径 + 视图OID字段值；域名或OID缺失时不生成链接
        part.setPageUrl(resolveInfoPageUrl(getString(row, "OID")));

        // 其余属性放入attrs，忽略大小写排除已单独提取的固定字段
        Map<String, Object> attrs = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!FIXED_FIELDS.contains(entry.getKey().toUpperCase())) {
                attrs.put(entry.getKey(), entry.getValue());
            }
        }
        part.setAttrs(attrs);
        return part;
    }

    /**
     * 将视图查询行转换为竞品下拉选项文本，格式为"部件编号,部件名称"。
     * 编号为空的脏数据返回null，由调用方跳过。
     *
     * @param row 视图查询行（key为大写列名）
     * @return 下拉选项文本；编号为空时返回null
     */
    public String toOptionLabel(Map<String, Object> row) {
        String wtPartNumber = getString(row, "WTPARTNUMBER");
        if (StringUtils.isBlank(wtPartNumber)) {
            return null;
        }
        return wtPartNumber + "," + getString(row, "NAME");
    }

    /**
     * 按部件编号拼接图片静态资源地址。
     * 编号为空或包含路径穿越字符（".."、"/"、"\"）时返回空串，防止拼出非法路径；
     * 不再做服务端文件存在性检查（图片由Windchill静态服务托管，加载失败由前端默认图兜底）。
     *
     * @param wtPartNumber 部件编号
     * @return 图片静态访问地址；编号非法时返回空串
     */
    private String resolveImage(String wtPartNumber) {
        if (StringUtils.isBlank(wtPartNumber)
                || wtPartNumber.contains("..") || wtPartNumber.contains("/") || wtPartNumber.contains("\\")) {
            return "";
        }
        String domain = StringUtils.removeEnd(pbiProperties.getDomain(), "/");
        String pathPrefix = pbiProperties.getImagePathPrefix();
        if (StringUtils.isAnyBlank(domain, pathPrefix)) {
            return "";
        }
        return domain + pathPrefix + wtPartNumber + IMAGE_SUFFIX;
    }

    /**
     * 拼接部件Windchill信息页地址：协议://当前登录账号:Basic认证密码@域名 + 信息页路径 + OID。
     * 账号取当前登录用户账号（请求线程中获取），密码取Nacos配置 pbi.api.basic-password，
     * 浏览器新标签页（顶层窗口导航）打开该地址时自动携带Basic凭据，不再弹出认证框。
     * 注意：Chrome仅允许顶层导航使用URL内嵌凭据，iframe等子资源会被拦截。
     * 域名、路径、OID、账号、密码任一缺失时返回空串，避免生成无效链接。
     *
     * @param oid 部件OID
     * @return 含Basic认证凭据的信息页完整地址；无法拼接时返回空串
     */
    private String resolveInfoPageUrl(String oid) {
        String domain = StringUtils.removeEnd(pbiProperties.getDomain(), "/");
        String infoPagePath = pbiProperties.getInfoPagePath();
        String account = AuthInfoUtils.getCurrentAccount();
        String password = pbiProperties.getBasicPassword();
        if (StringUtils.isAnyBlank(domain, infoPagePath, oid, account, password)) {
            return "";
        }
        try {
            URI uri = URI.create(domain);
            // 账号密码做URL编码，防止含特殊字符破坏URL结构
            String credentials = URLEncoder.encode(account, StandardCharsets.UTF_8)
                    + ":" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            String host = uri.getHost();
            int port = uri.getPort();
            return uri.getScheme() + "://" + credentials + "@" + host
                    + (port > 0 ? ":" + port : "") + infoPagePath + oid;
        } catch (Exception e) {
            log.error("拼接Windchill信息页地址失败，domain:{}", domain, e);
            return "";
        }
    }

    /**
     * 以字符串形式读取行中的列值，null安全
     *
     * @param row        查询行
     * @param columnName 列名（大写）
     * @return 字符串值，null时返回空串
     */
    private String getString(Map<String, Object> row, String columnName) {
        Object value = row.get(columnName);
        return value != null ? value.toString() : "";
    }
}
