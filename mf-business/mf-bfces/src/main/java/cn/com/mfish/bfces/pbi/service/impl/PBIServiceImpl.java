package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.service.PBIService;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI服务实现，负责分类节点、筛选属性、特征参数配置与产品型号查询的业务编排。
 * 远程Windchill调用统一委托 {@link PbiApiClient}，本类只保留数据库查询、
 * 视图列白名单组装与产品型号数据转换逻辑。
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PBIServiceImpl implements PBIService {

    /** PBI零件视图名 */
    private static final String PBI_PART_VIEW_NAME = "BFCES_PBI_PART_VIEW";

    /** 一级分类根节点名称 */
    private static final String ROOT_PARENT_NODE = "BFCES_ProductClassification";

    /** "ALL"兜底节点名称 */
    private static final String ALL_NODE = "ALL";

    /** 参数内部名称前缀，仅查询bfces自定义属性 */
    private static final String INTERNAL_NAME_PREFIX = "com.bfces.";

    /** 部件图片静态访问URL前缀，与Windchill codebase下的图片目录映射一致 */
    private static final String IMAGE_URL_PREFIX = "/Windchill/wtcore/test/part/image/";

    /** 部件图片文件后缀 */
    private static final String IMAGE_SUFFIX = ".png";

    /** 查询型号时必须携带的固定列 */
    private static final List<String> REQUIRED_COLUMNS = List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID");

    /** 已单独提取、不放入扩展属性attrs的固定字段（大写） */
    private static final Set<String> FIXED_FIELDS = Set.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID");

    private final PbiApiClient pbiApiClient;

    private final PbiParamConfigMapper pbiParamConfigMapper;

    private final PbiPartMapper pbiPartMapper;

    private final PbiProperties pbiProperties;

    /** 视图真实列名缓存（统一转为大写）；视图结构稳定，懒加载一次；为null表示尚未加载或加载失败 */
    private volatile Set<String> viewColumnCache;

    @Override
    public List<PbiNode> findNode1() {
        List<PbiNode> nodes = pbiApiClient.findChildNodes(ROOT_PARENT_NODE);
        // 末尾追加ALL兜底节点，供前端选择全部分类
        PbiNode allNode = new PbiNode();
        allNode.setName(ALL_NODE);
        allNode.setDisplayName(ALL_NODE);
        allNode.setDescription(ALL_NODE);
        nodes.add(allNode);
        return nodes;
    }

    @Override
    public List<PbiNode> findNode2(String parentNodeName) {
        return pbiApiClient.findChildNodes(parentNodeName);
    }

    @Override
    public List<PbiParamConfig> getParamConfig(String firstCategory) {
        LambdaQueryWrapper<PbiParamConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PbiParamConfig::getFirstCategory, firstCategory);
        queryWrapper.likeRight(PbiParamConfig::getInternalName, INTERNAL_NAME_PREFIX);
        return pbiParamConfigMapper.selectList(queryWrapper);
    }

    @Override
    public List<PbiPart> getPartsByClassification(String firstCategory, String productClassification) {
        // 1. 获取配置的列
        List<PbiParamConfig> paramConfigs = getParamConfig(firstCategory);

        // 2. 组装查询列：仅保留视图中真实存在的列，避免查询不存在的列导致ORA-00904
        Set<String> viewColumns = getViewColumns();
        List<String> columnList = new ArrayList<>();
        for (String requiredColumn : REQUIRED_COLUMNS) {
            addQueryColumn(columnList, viewColumns, requiredColumn);
        }
        if (paramConfigs != null) {
            for (PbiParamConfig config : paramConfigs) {
                addQueryColumn(columnList, viewColumns, config.getColumnName());
            }
        }

        // 没有任何可查询的列时直接返回，避免拼出非法SQL
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在任何可查询的列，终止本次查询", PBI_PART_VIEW_NAME);
            return new ArrayList<>();
        }
        String columns = String.join(", ", columnList);

        // 3. 查询视图并转换为产品型号对象
        List<Map<String, Object>> rows = pbiPartMapper.getPartsByClassification(productClassification, columns);
        List<PbiPart> parts = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return parts;
        }
        for (Map<String, Object> row : rows) {
            parts.add(convertToPart(row));
        }
        return parts;
    }

    @Override
    public List<String> getPartOptions(String firstCategory, String productClassification) {
        // 下拉选项只需编号、名称两列，经视图列存在性校验后查询，避免ORA-00904
        Set<String> viewColumns = getViewColumns();
        List<String> columnList = new ArrayList<>();
        addQueryColumn(columnList, viewColumns, "WTPARTNUMBER");
        addQueryColumn(columnList, viewColumns, "NAME");
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在编号或名称列，终止下拉选项查询", PBI_PART_VIEW_NAME);
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = pbiPartMapper.getPartsByClassification(
                productClassification, String.join(", ", columnList));
        List<String> options = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return options;
        }
        // 每项格式为"部件编号,部件名称"；编号为空的脏数据直接跳过
        for (Map<String, Object> row : rows) {
            String wtPartNumber = getString(row, "WTPARTNUMBER");
            if (StringUtils.isBlank(wtPartNumber)) {
                continue;
            }
            options.add(wtPartNumber + "," + getString(row, "NAME"));
        }
        return options;
    }

    /**
     * 将视图查询行转换为产品型号对象，提取固定字段并解析图片地址，其余列放入扩展属性
     *
     * @param row 视图查询行（key为大写列名）
     * @return 产品型号对象
     */
    private PbiPart convertToPart(Map<String, Object> row) {
        PbiPart part = new PbiPart();
        part.setWtPartNumber(getString(row, "WTPARTNUMBER"));
        part.setName(getString(row, "NAME"));
        part.setImage(resolveImage(part.getWtPartNumber()));
        // Windchill信息页地址：配置域名 + 信息页路径 + 视图OID字段值；域名或OID缺失时不生成链接
        String oid = getString(row, "OID");
        part.setPageUrl(resolveInfoPageUrl(oid));

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
     * 按部件编号解析图片地址：本地图片目录存在对应PNG文件时返回静态资源URL，否则返回空串。
     * 图片文件统一以"部件编号.png"命名，存放于 {@link PbiProperties#getImageDir()} 配置的目录。
     *
     * @param wtPartNumber 部件编号
     * @return 图片静态访问地址；文件不存在或编号非法时返回空串
     */
    private String resolveImage(String wtPartNumber) {
        // 编号为空或包含路径分隔符（防止目录穿越拼出非法路径）时不生成图片
        if (StringUtils.isBlank(wtPartNumber)
                || wtPartNumber.contains("..") || wtPartNumber.contains("/") || wtPartNumber.contains("\\")) {
            return "";
        }
        return StringUtils.removeEnd(pbiProperties.getDomain(), "/") + IMAGE_URL_PREFIX + wtPartNumber + IMAGE_SUFFIX;
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

    /**
     * 获取PBI零件视图真实存在的列名（大写集合），懒加载并缓存。
     * 数据字典查询失败时返回null，调用方按"不做存在性校验"降级，避免影响主流程，同时保留失败后自动重试的能力。
     *
     * @return 视图列名大写集合；加载失败时为null
     */
    private Set<String> getViewColumns() {
        if (viewColumnCache != null) {
            return viewColumnCache;
        }
        synchronized (this) {
            if (viewColumnCache != null) {
                return viewColumnCache;
            }
            try {
                List<String> columns = pbiPartMapper.getViewColumns();
                if (columns == null || columns.isEmpty()) {
                    log.warn("未从数据字典查询到视图{}的列信息，列存在性校验降级为不校验", PBI_PART_VIEW_NAME);
                    return null;
                }
                Set<String> columnSet = new HashSet<>();
                for (String column : columns) {
                    if (StringUtils.isNotBlank(column)) {
                        columnSet.add(column.trim().toUpperCase());
                    }
                }
                viewColumnCache = columnSet;
                log.info("加载视图{}列信息成功，共{}列", PBI_PART_VIEW_NAME, columnSet.size());
                return viewColumnCache;
            } catch (Exception e) {
                log.error("查询视图{}列信息失败，列存在性校验降级为不校验", PBI_PART_VIEW_NAME, e);
                return null;
            }
        }
    }

    /**
     * 向查询列列表追加列名：忽略大小写去重，并过滤掉含非法字符或视图中不存在的列
     *
     * @param columnList   已组装的查询列列表
     * @param viewColumns  视图真实列名集合（大写），为null时不做存在性校验（降级模式）
     * @param columnName   待追加的列名
     */
    private void addQueryColumn(List<String> columnList, Set<String> viewColumns, String columnName) {
        if (StringUtils.isBlank(columnName)) {
            return;
        }
        String column = columnName.trim();
        // 仅允许字母、数字、下划线，防止异常配置导致SQL语法错误或SQL注入
        if (!column.matches("^[A-Za-z0-9_]+$")) {
            log.warn("列名[{}]包含非法字符，已跳过", column);
            return;
        }
        // 忽略大小写去重
        for (String existingColumn : columnList) {
            if (existingColumn.equalsIgnoreCase(column)) {
                return;
            }
        }
        // 视图存在性校验（Oracle未加引号的标识符不区分大小写，统一按大写比对）
        if (viewColumns != null && !viewColumns.contains(column.toUpperCase())) {
            log.warn("列[{}]在视图{}中不存在，已跳过查询", column, PBI_PART_VIEW_NAME);
            return;
        }
        columnList.add(column);
    }

    @Override
    public List<PbiFilterProperty> getFilterProperties(String nodeName) {
        return pbiApiClient.getAttrs(nodeName);
    }
}
