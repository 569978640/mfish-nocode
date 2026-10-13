package cn.com.mfish.bfces.pbi.client;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.common.core.exception.MyRuntimeException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @description: Windchill(PBI)远程接口客户端。
 * 统一封装请求头、URL编码、响应状态校验、JSON报文解析，
 * 业务接口失败抛出MyRuntimeException。
 * @author: mfish
 * @date: 2026-10-13
 * @version: V1.1.0
 */
@Slf4j
@Component
public class PbiApiClient {

    /** 查询分类子节点接口路径 */
    private static final String CHILD_NODE_PATH = "/Windchill/ptc1/csm/getChildNode";

    /** 查询筛选属性接口路径 */
    private static final String ATTRS_PATH = "/Windchill/ptc1/csm/getAttrs";

    private final PbiProperties properties;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PbiApiClient(PbiProperties properties,
                        @Qualifier("pbiRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 查询指定父节点下的分类子节点
     *
     * @param parentNodeName 父节点名称
     * @return 子节点列表，远程无数据时返回空集合
     */
    public List<PbiNode> findChildNodes(String parentNodeName) {
        String url = buildUrl(CHILD_NODE_PATH)
                .queryParam("parentNodeName", parentNodeName)
                .toUriString();
        JsonNode objArray = doGetForBizObj(url, "获取PBI子节点");
        return parseNodeArray(objArray);
    }

    /**
     * 查询筛选属性
     *
     * @param nodeName 节点名称，为空时不带该参数
     * @return 筛选属性列表，远程无数据时返回空集合
     */
    public List<PbiFilterProperty> getAttrs(String nodeName) {
        UriComponentsBuilder builder = buildUrl(ATTRS_PATH);
        if (StringUtils.isNotBlank(nodeName)) {
            builder.queryParam("nodeName", nodeName);
        }
        JsonNode objArray = doGetForBizObj(builder.toUriString(), "获取PBI筛选属性");
        return parseFilterPropertyArray(objArray);
    }

    /**
     * 发送GET请求并提取Windchill标准报文中的obj节点。
     * 报文约定：{"success": true, "obj": ...}；HTTP非2xx、success为false或解析异常均抛出业务异常。
     *
     * @param url     完整请求地址
     * @param bizName 业务名称（用于异常信息）
     * @return 报文中的obj节点
     */
    private JsonNode doGetForBizObj(String url, String bizName) {
        try {
            HttpEntity<String> entity = new HttpEntity<>(buildAuthHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new MyRuntimeException(bizName + "接口返回状态异常: " + response.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            boolean success = root.has("success") && root.get("success").asBoolean();
            if (!success || !root.has("obj")) {
                throw new MyRuntimeException(bizName + "接口返回业务失败: " + response.getBody());
            }
            return root.get("obj");
        } catch (MyRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用{}接口失败, url: {}", bizName, url, e);
            throw new MyRuntimeException("调用" + bizName + "接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析分类节点数组
     *
     * @param objArray obj数组节点
     * @return 分类节点列表
     */
    private List<PbiNode> parseNodeArray(JsonNode objArray) {
        List<PbiNode> nodes = new ArrayList<>();
        if (objArray == null || !objArray.isArray()) {
            return nodes;
        }
        for (JsonNode item : objArray) {
            PbiNode node = new PbiNode();
            node.setName(getText(item, "NAME"));
            node.setDisplayName(getText(item, "DISPLAY_NAME"));
            node.setParentName(getText(item, "PARENT_NAME"));
            node.setParentDisplayName(getText(item, "PARENT_DISPLAY_NAME"));
            node.setImage(getText(item, "IMAGE"));
            node.setDescription(getText(item, "DESCRIPTION"));
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 解析筛选属性数组
     *
     * @param objArray obj数组节点
     * @return 筛选属性列表
     */
    private List<PbiFilterProperty> parseFilterPropertyArray(JsonNode objArray) {
        List<PbiFilterProperty> properties = new ArrayList<>();
        if (objArray == null || !objArray.isArray()) {
            return properties;
        }
        for (JsonNode item : objArray) {
            PbiFilterProperty property = new PbiFilterProperty();
            property.setDisplayName(getText(item, "displayName"));
            property.setName(getText(item, "name"));
            property.setType(getText(item, "type"));
            if (item.has("values") && item.get("values").isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode valueNode : item.get("values")) {
                    values.add(valueNode.asText());
                }
                property.setValues(values);
            }
            properties.add(property);
        }
        return properties;
    }

    /**
     * 读取JSON节点中指定字段的文本值，字段不存在时返回null
     *
     * @param node      JSON节点
     * @param fieldName 字段名
     * @return 文本值或null
     */
    private String getText(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }

    /**
     * 构建带鉴权信息的请求头
     *
     * @return 请求头
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, properties.getAuthorization());
        return headers;
    }

    /**
     * 基于配置域名构建URL构建器，统一按UTF-8编码查询参数（支持中文参数）
     *
     * @param path 接口路径
     * @return URI构建器
     */
    private UriComponentsBuilder buildUrl(String path) {
        return UriComponentsBuilder.fromUriString(properties.getDomain() + path)
                .encode(StandardCharsets.UTF_8);
    }
}
