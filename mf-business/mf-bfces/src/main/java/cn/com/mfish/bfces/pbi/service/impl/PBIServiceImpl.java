package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.service.PBIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
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

    @Value("${pbi.api.domain}")
    private String pbiApiDomain;

    @Value("${pbi.api.authorization}")
    private String pbiApiAuthorization;

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

    private RestTemplate getUnsafeRestTemplate() {
        try {
            // 信任所有证书
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
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
}
