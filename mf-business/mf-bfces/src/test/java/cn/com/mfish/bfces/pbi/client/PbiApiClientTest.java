package cn.com.mfish.bfces.pbi.client;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.common.core.exception.MyRuntimeException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @description: PBI远程接口客户端单元测试，使用MockRestServiceServer拦截HTTP请求，不联网
 * @author: mfish
 * @date: 2026-10-13
 */
class PbiApiClientTest {

    private static final String DOMAIN = "https://plm13test.bfces.com";
    private static final String TOKEN = "Basic amFjazI=";

    private MockRestServiceServer mockServer;

    private PbiApiClient apiClient;

    @BeforeEach
    void setUp() {
        PbiProperties properties = new PbiProperties();
        properties.setDomain(DOMAIN);
        properties.setAuthorization(TOKEN);
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        apiClient = new PbiApiClient(properties, restTemplate);
    }

    @Test
    void findChildNodes_成功响应_正确映射字段并编码中文参数() {
        String body = "{\"success\":true,\"obj\":[{"
                + "\"NAME\":\"node1\","
                + "\"DISPLAY_NAME\":\"节点1\","
                + "\"PARENT_NAME\":\"分类A\","
                + "\"PARENT_DISPLAY_NAME\":\"一级分类A\","
                + "\"IMAGE\":\"/img/1.png\","
                + "\"DESCRIPTION\":\"描述\"}]}";
        mockServer.expect(requestTo(Matchers.containsString("/Windchill/ptc1/csm/getChildNode")))
                .andExpect(requestTo(Matchers.containsString("parentNodeName=%E5%88%86%E7%B1%BBB")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<PbiNode> nodes = apiClient.findChildNodes("分类B");

        mockServer.verify();
        assertThat(nodes).hasSize(1);
        PbiNode node = nodes.get(0);
        assertThat(node.getName()).isEqualTo("node1");
        assertThat(node.getDisplayName()).isEqualTo("节点1");
        assertThat(node.getParentName()).isEqualTo("分类A");
        assertThat(node.getParentDisplayName()).isEqualTo("一级分类A");
        assertThat(node.getImage()).isEqualTo("/img/1.png");
        assertThat(node.getDescription()).isEqualTo("描述");
    }

    @Test
    void findChildNodes_http500_抛出业务异常() {
        mockServer.expect(requestTo(Matchers.containsString("getChildNode")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        assertThatThrownBy(() -> apiClient.findChildNodes("root"))
                .isInstanceOf(MyRuntimeException.class)
                .hasMessageContaining("获取PBI子节点");
    }

    @Test
    void findChildNodes_success为false_抛出业务异常() {
        mockServer.expect(requestTo(Matchers.containsString("getChildNode")))
                .andRespond(withSuccess("{\"success\":false,\"obj\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.findChildNodes("root"))
                .isInstanceOf(MyRuntimeException.class)
                .hasMessageContaining("业务失败");
    }

    @Test
    void findChildNodes_obj不是数组_返回空集合() {
        mockServer.expect(requestTo(Matchers.containsString("getChildNode")))
                .andRespond(withSuccess("{\"success\":true,\"obj\":{}}", MediaType.APPLICATION_JSON));

        assertThat(apiClient.findChildNodes("root")).isEmpty();
    }

    @Test
    void getAttrs_不传节点名_URL不带nodeName且正确映射values() {
        String body = "{\"success\":true,\"obj\":[{"
                + "\"displayName\":\"材质\","
                + "\"name\":\"com.bfces.MaterialType\","
                + "\"type\":\"string\","
                + "\"values\":[\"铁\",\"铝\"]},"
                + "{\"displayName\":\"开关\",\"name\":\"com.bfces.OSP\",\"type\":\"boolean\"}]}";
        mockServer.expect(requestTo(Matchers.allOf(
                        Matchers.containsString("/Windchill/ptc1/csm/getAttrs"),
                        Matchers.not(Matchers.containsString("nodeName")))))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<PbiFilterProperty> properties = apiClient.getAttrs(null);

        mockServer.verify();
        assertThat(properties).hasSize(2);
        PbiFilterProperty first = properties.get(0);
        assertThat(first.getDisplayName()).isEqualTo("材质");
        assertThat(first.getName()).isEqualTo("com.bfces.MaterialType");
        assertThat(first.getType()).isEqualTo("string");
        assertThat(first.getValues()).containsExactly("铁", "铝");
        // 无values字段时为null
        assertThat(properties.get(1).getValues()).isNull();
    }

    @Test
    void getAttrs_传入节点名_参数被编码() {
        mockServer.expect(requestTo(Matchers.containsString("nodeName=%E8%8A%82%E7%82%B9X")))
                .andRespond(withSuccess("{\"success\":true,\"obj\":[]}", MediaType.APPLICATION_JSON));

        List<PbiFilterProperty> result = apiClient.getAttrs("节点X");

        mockServer.verify();
        assertThat(result).isEmpty();
    }
}
