package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.service.PBIService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.nio.charset.StandardCharsets;

import org.springframework.http.converter.StringHttpMessageConverter;

/**
 * @description: PBI服务实现
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.0.0
 */
@Slf4j
@Service
public class PBIServiceImpl implements PBIService {

    /** PBI零件视图名 */
    private static final String PBI_PART_VIEW_NAME = "BFCES_PBI_PART_VIEW";

    /** 视图真实列名缓存（统一转为大写）；视图结构稳定，懒加载一次；为null表示尚未加载或加载失败 */
    private volatile Set<String> viewColumnCache;

    @Value("${pbi.api.domain}")
    private String pbiApiDomain;

    @Value("${pbi.api.authorization}")
    private String pbiApiAuthorization;

    @Autowired
    private PbiParamConfigMapper pbiParamConfigMapper;

    @Autowired
    private PbiPartMapper pbiPartMapper;

    @Override
    public List<PbiNode> findNode1() {
        String url = pbiApiDomain + "/Windchill/ptc1/csm/getChildNode?parentNodeName=BFCES_ProductClassification";
//        RestTemplate restTemplate = new RestTemplate();
        RestTemplate restTemplate = getUnsafeRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", pbiApiAuthorization);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<PbiNode> nodes = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("obj")) {
                    JsonNode objNode = root.get("obj");
                    if (objNode.isArray()) {
                        for (JsonNode node : objNode) {
                            PbiNode pbiNode = new PbiNode();
                            if (node.has("NAME")) {
                                pbiNode.setName(node.get("NAME").asText());
                            }
                            if (node.has("DISPLAY_NAME")) {
                                pbiNode.setDisplayName(node.get("DISPLAY_NAME").asText());
                            }
                            if (node.has("PARENT_NAME")) {
                                pbiNode.setParentName(node.get("PARENT_NAME").asText());
                            }
                            if (node.has("PARENT_DISPLAY_NAME")) {
                                pbiNode.setParentDisplayName(node.get("PARENT_DISPLAY_NAME").asText());
                            }
                            if (node.has("IMAGE")) {
                                pbiNode.setImage(node.get("IMAGE").asText());
                            }
                            if (node.has("DESCRIPTION")) {
                                pbiNode.setDescription(node.get("DESCRIPTION").asText());
                            }
                            nodes.add(pbiNode);
                        }
                    }
                }
            } else {
                log.error("获取PBI子节点接口返回状态异常: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用获取PBI子节点接口失败", e);
        }
        PbiNode pbiNode = new PbiNode();
        pbiNode.setName("ALL");
        pbiNode.setDisplayName("ALL");
        pbiNode.setDescription("ALL");
        nodes.add(pbiNode);
        return nodes;
    }

    @Override
    public List<PbiNode> findNode2(String parentNodeName) {
        String url = pbiApiDomain + "/Windchill/ptc1/csm/getChildNode?parentNodeName=" + parentNodeName;
        RestTemplate restTemplate = getUnsafeRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", pbiApiAuthorization);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<PbiNode> nodes = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("obj")) {
                    JsonNode objNode = root.get("obj");
                    if (objNode.isArray()) {
                        for (JsonNode node : objNode) {
                            PbiNode pbiNode = new PbiNode();
                            if (node.has("NAME")) {
                                pbiNode.setName(node.get("NAME").asText());
                            }
                            if (node.has("DISPLAY_NAME")) {
                                pbiNode.setDisplayName(node.get("DISPLAY_NAME").asText());
                            }
                            if (node.has("PARENT_NAME")) {
                                pbiNode.setParentName(node.get("PARENT_NAME").asText());
                            }
                            if (node.has("PARENT_DISPLAY_NAME")) {
                                pbiNode.setParentDisplayName(node.get("PARENT_DISPLAY_NAME").asText());
                            }
                            if (node.has("IMAGE")) {
                                pbiNode.setImage(node.get("IMAGE").asText());
                            }
                            if (node.has("DESCRIPTION")) {
                                pbiNode.setDescription(node.get("DESCRIPTION").asText());
                            }
                            nodes.add(pbiNode);
                        }
                    }
                }
            } else {
                log.error("获取PBI子节点接口返回状态异常: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用获取PBI子节点接口失败", e);
        }
        return nodes;
    }

    @Override
    public List<PbiParamConfig> getParamConfig(String firstCategory) {
        LambdaQueryWrapper<PbiParamConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PbiParamConfig::getFirstCategory, firstCategory);
        queryWrapper.likeRight(PbiParamConfig::getInternalName, "com.bfces.");
        return pbiParamConfigMapper.selectList(queryWrapper);
    }

    @Override
    public List<PbiPart> getPartsByClassification(String firstCategory, String productClassification) {
        // 1. 获取配置的列
        List<PbiParamConfig> paramConfigs = getParamConfig(firstCategory);

        // 2. 组装查询列：仅保留视图中真实存在的列，避免查询不存在的列导致ORA-00904
        Set<String> viewColumns = getViewColumns();
        List<String> columnList = new ArrayList<>();
        // 必须的列（视图中不存在时会被自动跳过）
        addQueryColumn(columnList, viewColumns, "OID");
        addQueryColumn(columnList, viewColumns, "WTPARTNUMBER");
        addQueryColumn(columnList, viewColumns, "NAME");
        addQueryColumn(columnList, viewColumns, "APPLICATIONID");

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

        List<Map<String, Object>> list = pbiPartMapper.getPartsByClassification(productClassification, columns);
        List<PbiPart> parts = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return parts;
        }

        for (Map<String, Object> map : list) {
            PbiPart part = new PbiPart();

            // 提取 wtPartNumber
            Object wtPartNumber = map.get("WTPARTNUMBER");
            part.setWtPartNumber(wtPartNumber != null ? wtPartNumber.toString() : "");

            // 提取 name
            Object name = map.get("NAME");
            part.setName(name != null ? name.toString() : "");

            // 拼接 image
            Object oid = map.get("OID");
            Object applicationId = map.get("APPLICATIONID");

            if (oid != null && applicationId != null
                    && StringUtils.isNotBlank(oid.toString())
                    && StringUtils.isNotBlank(applicationId.toString())
                    && !"wt.content.ApplicationData:".equals(applicationId.toString())) {
                String imageUrl = "https://" + pbiApiDomain + "/Windchill/servlet/AttachmentsDownloadDirectionServlet?oid="
                        + oid.toString() + "&cioids=" + applicationId.toString() + "&role=SECONDARY";
                
                // 请求 imageUrl 获取真正的下载链接
                String realDownloadUrl = getRealDownloadUrl(imageUrl);
                part.setImage(realDownloadUrl);
            } else {
                part.setImage("");
            }

            // 将其他属性放入 attrs
            Map<String, Object> attrs = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                // 忽略已经提取的字段，忽略大小写
                if (!"WTPARTNUMBER".equalsIgnoreCase(key) &&
                        !"NAME".equalsIgnoreCase(key) &&
                        !"OID".equalsIgnoreCase(key) &&
                        !"APPLICATIONID".equalsIgnoreCase(key)) {
                    attrs.put(key, entry.getValue());
                }
            }
            part.setAttrs(attrs);

            parts.add(part);
        }
        return parts;
    }

    /**
     * 获取PBI零件视图真实存在的列名（大写集合），懒加载并缓存。
     * 数据字典查询失败时返回null，调用方按"不做存在性校验"降级，避免影响主流程，同时保留失败后自动重试的能力
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
     * @param columnList 已组装的查询列列表
     * @param viewColumns 视图真实列名集合（大写），为null时不做存在性校验（降级模式）
     * @param columnName 待追加的列名
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
        for (String existingCol : columnList) {
            if (existingCol.equalsIgnoreCase(column)) {
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
        String url = "https://plm13test.bfces.com/Windchill/ptc1/csm/getAttrs";
        if (StringUtils.isNotBlank(nodeName)) {
            url += "?nodeName=" + nodeName;
        }
        
        RestTemplate restTemplate = getUnsafeRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", pbiApiAuthorization);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        List<PbiFilterProperty> properties = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("obj")) {
                    JsonNode objNode = root.get("obj");
                    if (objNode.isArray()) {
                        for (JsonNode node : objNode) {
                            PbiFilterProperty property = new PbiFilterProperty();
                            if (node.has("displayName")) {
                                property.setDisplayName(node.get("displayName").asText());
                            }
                            if (node.has("name")) {
                                property.setName(node.get("name").asText());
                            }
                            if (node.has("type")) {
                                property.setType(node.get("type").asText());
                            }
                            if (node.has("values") && node.get("values").isArray()) {
                                List<String> values = new ArrayList<>();
                                for (JsonNode valueNode : node.get("values")) {
                                    values.add(valueNode.asText());
                                }
                                property.setValues(values);
                            }
                            properties.add(property);
                        }
                    }
                }
            } else {
                log.error("获取PBI筛选属性接口返回状态异常: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("调用获取PBI筛选属性接口失败", e);
        }
        return properties;
    }

    private RestTemplate getUnsafeRestTemplate() {
        try {
            // 信任所有证书
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            // 关闭主机名校验
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            RestTemplate restTemplate = new RestTemplate(factory);
            // 解决中文乱码问题
            restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
            return restTemplate;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发送GET请求获取真实的下载链接
     * @param url 请求地址
     * @return 真实下载链接，获取失败则返回原地址或空
     */
    private String getRealDownloadUrl(String url) {
        try {
            RestTemplate restTemplate = getUnsafeRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", pbiApiAuthorization);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String body = response.getBody();
                // 提取 downloadURL 的值，匹配 var downloadURL = " 链接 "; 的格式
                // 考虑到其中可能有空格、特殊字符，使用正则匹配
                Pattern pattern = Pattern.compile("var\\s+downloadURL\\s*=\\s*\"\\s*`?([^\"]+?)`?\\s*\"");
                Matcher matcher = pattern.matcher(body);
                if (matcher.find()) {
                    String downloadUrl = matcher.group(1);
                    // 清理可能存在的首尾空格或者反引号
                    if (downloadUrl != null) {
                        downloadUrl = downloadUrl.trim();
                        if (downloadUrl.startsWith("`") && downloadUrl.endsWith("`")) {
                            downloadUrl = downloadUrl.substring(1, downloadUrl.length() - 1).trim();
                        }
                        return downloadUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取真实下载链接失败: {}", url, e);
        }
        return url; // 如果解析失败，回退返回原始拼接URL，或者返回 "" 都可以
    }
}
