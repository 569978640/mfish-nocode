package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @description: PBI业务服务单元测试，纯Mockito模拟Mapper与远程客户端，不连库不联网
 * @author: mfish
 * @date: 2026-10-13
 */
@ExtendWith(MockitoExtension.class)
class PBIServiceImplTest {

    @Mock
    private PbiApiClient pbiApiClient;

    @Mock
    private PbiParamConfigMapper pbiParamConfigMapper;

    @Mock
    private PbiPartMapper pbiPartMapper;

    /** 图片相关配置，每个用例前重新初始化，imageDir指向临时目录 */
    private PbiProperties pbiProperties;

    private PBIServiceImpl pbiService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        pbiProperties = new PbiProperties();
        pbiProperties.setDomain("https://plm13test.bfces.com");
        pbiProperties.setImageDir(tempDir.toString());
        pbiService = new PBIServiceImpl(pbiApiClient, pbiParamConfigMapper, pbiPartMapper, pbiProperties);
    }

    @Test
    void findNode1_远程结果后追加ALL兜底节点() {
        PbiNode remoteNode = new PbiNode();
        remoteNode.setName("CAT_A");
        when(pbiApiClient.findChildNodes("BFCES_ProductClassification"))
                .thenReturn(new java.util.ArrayList<>(List.of(remoteNode)));

        List<PbiNode> result = pbiService.findNode1();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("CAT_A");
        PbiNode allNode = result.get(1);
        assertThat(allNode.getName()).isEqualTo("ALL");
        assertThat(allNode.getDisplayName()).isEqualTo("ALL");
        assertThat(allNode.getDescription()).isEqualTo("ALL");
    }

    @Test
    void findNode2_直接委托远程客户端() {
        PbiNode node = new PbiNode();
        node.setName("sub");
        when(pbiApiClient.findChildNodes("parent")).thenReturn(List.of(node));

        List<PbiNode> result = pbiService.findNode2("parent");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("sub");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartsByClassification_列白名单过滤去重_正确组装型号与扩展属性() throws Exception {
        // 视图真实存在的列
        when(pbiPartMapper.getViewColumns())
                .thenReturn(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "COL_A"));
        // 参数配置：COL_A有效、COL_B视图不存在、非法字符列、与固定列重复的oid
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of(
                buildConfig("COL_A"),
                buildConfig("COL_B"),
                buildConfig("bad col;"),
                buildConfig("oid")
        ));
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-001");
        row.put("NAME", "电机");
        row.put("OID", "OR:wt.part.WTPart:123");
        row.put("APPLICATIONID", "app-1");
        row.put("COL_A", "值A");
        // 图片目录中存在与部件编号同名的PNG文件
        Files.createFile(tempDir.resolve("P-001.png"));
        when(pbiPartMapper.getPartsByClassification(anyString(), anyString())).thenReturn(List.of(row));

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        // 校验最终查询列：固定列+有效配置列，非法/不存在/重复列均被剔除
        ArgumentCaptor<String> columnsCaptor = ArgumentCaptor.forClass(String.class);
        verify(pbiPartMapper).getPartsByClassification(eq("产品分类"), columnsCaptor.capture());
        assertThat(columnsCaptor.getValue())
                .isEqualTo("OID, WTPARTNUMBER, NAME, APPLICATIONID, COL_A");

        assertThat(result).hasSize(1);
        PbiPart part = result.get(0);
        assertThat(part.getWtPartNumber()).isEqualTo("P-001");
        assertThat(part.getName()).isEqualTo("电机");
        assertThat(part.getImage())
                .isEqualTo("https://plm13test.bfces.com/Windchill/wtcore/test/part/image/P-001.png");
        // 扩展属性仅保留配置列，固定字段被排除
        assertThat(part.getAttrs()).hasSize(1);
        assertThat(part.getAttrs().get("COL_A")).isEqualTo("值A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartsByClassification_图片文件不存在_图片为空() {
        when(pbiPartMapper.getViewColumns())
                .thenReturn(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID"));
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> row = new HashMap<>();
        row.put("WTPARTNUMBER", "P-002");
        row.put("NAME", "无图件");
        row.put("OID", "oid-2");
        row.put("APPLICATIONID", "wt.content.ApplicationData:");
        when(pbiPartMapper.getPartsByClassification(anyString(), anyString())).thenReturn(List.of(row));

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        // 临时目录下不存在P-002.png，图片地址为空串
        assertThat(result.get(0).getImage()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartsByClassification_无任何可查列_直接返回空不查询视图() {
        // 视图只有一列无关列，固定列与配置列全部被过滤
        when(pbiPartMapper.getViewColumns()).thenReturn(List.of("OTHER"));
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of());

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        assertThat(result).isEmpty();
        verify(pbiPartMapper, never()).getPartsByClassification(anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartsByClassification_视图列查询异常_降级为不校验仍可查询() {
        // 数据字典查询抛异常：列存在性校验降级，配置列即使无法验证也保留
        when(pbiPartMapper.getViewColumns()).thenThrow(new RuntimeException("字典表不可用"));
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of(buildConfig("COL_X")));
        when(pbiPartMapper.getPartsByClassification(anyString(), anyString())).thenReturn(List.of());

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        assertThat(result).isEmpty();
        ArgumentCaptor<String> columnsCaptor = ArgumentCaptor.forClass(String.class);
        verify(pbiPartMapper).getPartsByClassification(anyString(), columnsCaptor.capture());
        assertThat(columnsCaptor.getValue()).contains("COL_X");
    }

    @Test
    void getFilterProperties_直接委托远程客户端() {
        PbiFilterProperty property = new PbiFilterProperty();
        property.setName("com.bfces.OSP");
        when(pbiApiClient.getAttrs("node")).thenReturn(List.of(property));

        List<PbiFilterProperty> result = pbiService.getFilterProperties("node");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("com.bfces.OSP");
    }

    /**
     * 构建仅设置列名的参数配置
     *
     * @param columnName 列名
     * @return 参数配置对象
     */
    private PbiParamConfig buildConfig(String columnName) {
        PbiParamConfig config = new PbiParamConfig();
        config.setColumnName(columnName);
        return config;
    }
}
