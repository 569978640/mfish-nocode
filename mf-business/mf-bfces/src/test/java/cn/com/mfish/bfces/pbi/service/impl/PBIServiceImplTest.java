package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.support.PbiPartConverter;
import cn.com.mfish.bfces.pbi.support.PbiViewColumnSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @description: PBI业务服务编排单元测试，全部依赖均为Mockito模拟，不连库不联网。
 * 视图列白名单与行转换的细节分别由 PbiViewColumnSelectorTest、PbiPartConverterTest 覆盖。
 * @author: mfish
 * @date: 2026-10-14
 */
@ExtendWith(MockitoExtension.class)
class PbiServiceImplTest {

    @Mock
    private PbiApiClient pbiApiClient;

    @Mock
    private PbiParamConfigMapper pbiParamConfigMapper;

    @Mock
    private PbiPartMapper pbiPartMapper;

    @Mock
    private PbiViewColumnSelector viewColumnSelector;

    @Mock
    private PbiPartConverter partConverter;

    private PbiServiceImpl pbiService;

    @BeforeEach
    void setUp() {
        pbiService = new PbiServiceImpl(pbiApiClient, pbiParamConfigMapper,
                pbiPartMapper, viewColumnSelector, partConverter);
    }

    @Test
    void findNode1_远程结果后追加ALL兜底节点() {
        PbiNode remoteNode = new PbiNode();
        remoteNode.setName("CAT_A");
        when(pbiApiClient.findChildNodes("BFCES_ProductClassification"))
                .thenReturn(new ArrayList<>(List.of(remoteNode)));

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
    void getPartsByClassification_候选列含固定列与配置列_查询后逐行转换() {
        PbiParamConfig config = new PbiParamConfig();
        config.setColumnName("COL_A");
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of(config));
        // 白名单选择器返回固定列+配置列
        when(viewColumnSelector.buildQueryColumns(anyList()))
                .thenReturn(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "COL_A"));
        Map<String, Object> row = new HashMap<>();
        when(pbiPartMapper.getPartsByClassification(eq("产品分类"), anyString())).thenReturn(List.of(row));
        PbiPart converted = new PbiPart();
        converted.setWtPartNumber("P-001");
        when(partConverter.toPart(row)).thenReturn(converted);

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        // 候选列顺序：固定列在前、配置列在后
        ArgumentCaptor<List<String>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(viewColumnSelector).buildQueryColumns(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue())
                .containsExactly("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "COL_A");
        // SQL列按选择器返回值原样拼接
        ArgumentCaptor<String> columnsCaptor = ArgumentCaptor.forClass(String.class);
        verify(pbiPartMapper).getPartsByClassification(eq("产品分类"), columnsCaptor.capture());
        assertThat(columnsCaptor.getValue())
                .isEqualTo("OID, WTPARTNUMBER, NAME, APPLICATIONID, COL_A");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getWtPartNumber()).isEqualTo("P-001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartsByClassification_无可查列_直接返回空不查询视图() {
        when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of());
        when(viewColumnSelector.buildQueryColumns(anyList())).thenReturn(List.of());

        List<PbiPart> result = pbiService.getPartsByClassification("一级分类", "产品分类");

        assertThat(result).isEmpty();
        verify(pbiPartMapper, never()).getPartsByClassification(anyString(), anyString());
        verify(partConverter, never()).toPart(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPartOptions_编号为空的脏数据被跳过() {
        when(viewColumnSelector.buildQueryColumns(anyList()))
                .thenReturn(List.of("WTPARTNUMBER", "NAME"));
        // 两行内容必须可区分：两个内容相等的Map作为stub参数时，后注册的stub会遮蔽前一个
        Map<String, Object> validRow = new HashMap<>();
        validRow.put("WTPARTNUMBER", "P-001");
        validRow.put("NAME", "电机");
        Map<String, Object> dirtyRow = new HashMap<>();
        dirtyRow.put("NAME", "无编号件");
        when(pbiPartMapper.getPartsByClassification(anyString(), anyString()))
                .thenReturn(List.of(validRow, dirtyRow));
        when(partConverter.toOptionLabel(validRow)).thenReturn("P-001,电机");
        when(partConverter.toOptionLabel(dirtyRow)).thenReturn(null);

        List<String> result = pbiService.getPartOptions("一级分类", "产品分类");

        assertThat(result).containsExactly("P-001,电机");
    }

    @Test
    void getPartOptions_无可查列_直接返回空不查询视图() {
        when(viewColumnSelector.buildQueryColumns(anyList())).thenReturn(List.of());

        List<String> result = pbiService.getPartOptions("一级分类", "产品分类");

        assertThat(result).isEmpty();
        verify(pbiPartMapper, never()).getPartsByClassification(anyString(), anyString());
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
}
