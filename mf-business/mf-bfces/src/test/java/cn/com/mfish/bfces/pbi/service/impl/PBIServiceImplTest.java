package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.cache.PbiPartCacheLoader;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheRepository;
import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.support.PbiFamilyTreeBuilder;
import cn.com.mfish.bfces.pbi.support.PbiPartConverter;
import cn.com.mfish.bfces.pbi.support.PbiPartRowQuery;
import cn.com.mfish.bfces.pbi.support.PbiViewColumnSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @description: PBI部件服务缓存链路单元测试：实时ID索引、Hash内存投影、异常降级宽行、下拉实时窄查、详情编号定位。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PBIServiceImplTest {

    @Mock
    private PbiApiClient pbiApiClient;
    @Mock
    private PbiParamConfigMapper pbiParamConfigMapper;
    @Mock
    private PbiPartMapper pbiPartMapper;
    @Mock
    private PbiViewColumnSelector viewColumnSelector;
    @Mock
    private PbiFamilyTreeBuilder familyTreeBuilder;
    @Mock
    private PbiPartCacheRepository partCacheRepository;
    @Mock
    private PbiPartCacheLoader partCacheLoader;

    private PbiProperties properties;
    private PbiServiceImpl service;

    /** 构造包含固定列与POWER扩展列的完整属性Hash */
    private static Map<String, String> fullHash(String partId, String number) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(PbiPartCacheRepository.PART_ID_COLUMN, partId);
        hash.put("OID", "OID-" + partId);
        hash.put("WTPARTNUMBER", number);
        hash.put("NAME", "部件" + partId);
        hash.put("APPLICATIONID", "APP-" + partId);
        hash.put("POWER", "5kW");
        return hash;
    }

    @BeforeEach
    void setUp() {
        properties = new PbiProperties();
        // 转换器使用真实实例（域名未配置时图片/信息页地址为空串，不影响属性断言）
        PbiPartConverter partConverter = new PbiPartConverter(properties);
        // 行查询链路使用真实实例（内部装配实时ID+Hash缓存+宽行降级），mock仅停在Mapper/缓存加载器边界
        PbiPartRowQuery partRowQuery =
                new PbiPartRowQuery(properties, partCacheLoader, pbiPartMapper, viewColumnSelector);
        service = new PbiServiceImpl(pbiApiClient, pbiParamConfigMapper, pbiPartMapper, viewColumnSelector,
                partConverter, familyTreeBuilder, partCacheRepository, partRowQuery);

        // 一级分类配置一个POWER特性列；白名单选择器模拟"全部候选列校验通过"
        // lenient：下拉选项与清缓存用例不触达列配置/白名单组装，严格模式下避免误报冗余stub
        PbiParamConfig powerConfig = new PbiParamConfig();
        powerConfig.setColumnName("POWER");
        org.mockito.Mockito.lenient().when(pbiParamConfigMapper.selectList(any())).thenReturn(List.of(powerConfig));
        org.mockito.Mockito.lenient().when(viewColumnSelector.buildQueryColumns(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 列表查询_ID与属性Hash均命中_不查Oracle宽行() {
        when(partCacheLoader.loadCategoryIds("CAT")).thenReturn(List.of("1", "2"));
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        hashes.put("1", fullHash("1", "P1"));
        hashes.put("2", fullHash("2", "P2"));
        when(partCacheLoader.loadHashes(eq(List.of("1", "2")), eq("category:CAT"), any())).thenReturn(hashes);

        List<PbiPart> parts = service.getPartsByClassification("一级分类", "CAT");

        assertEquals(2, parts.size());
        assertEquals("P1", parts.get(0).getWtPartNumber());
        assertEquals("P2", parts.get(1).getWtPartNumber());
        // 扩展列经投影后进入attrs，key为大写列名（与原Oracle返回行为一致）
        assertEquals("5kW", parts.get(0).getAttrs().get("POWER"));
        verify(pbiPartMapper, never()).getPartsByClassification(anyString(), anyString());
        verify(pbiPartMapper, never()).findIdsByClassification(anyString());
    }

    @Test
    void 列表查询_ID由加载器返回_去重trim与短缓存回填由加载器负责() {
        when(partCacheLoader.loadCategoryIds("CAT")).thenReturn(List.of("1", "2"));
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        hashes.put("1", fullHash("1", "P1"));
        hashes.put("2", fullHash("2", "P2"));
        when(partCacheLoader.loadHashes(eq(List.of("1", "2")), eq("category:CAT"), any())).thenReturn(hashes);

        List<PbiPart> parts = service.getPartsByClassification("一级分类", "CAT");

        assertEquals(2, parts.size());
        // ID列表的实时查询、去重与短缓存回填均封装在加载器中，Service只负责编排
        verify(partCacheLoader).loadCategoryIds("CAT");
    }

    @Test
    void 列表查询_ID为空列表_直接返回空且不加载Hash() {
        when(partCacheLoader.loadCategoryIds("CAT_EMPTY")).thenReturn(new ArrayList<>());

        List<PbiPart> parts = service.getPartsByClassification("一级分类", "CAT_EMPTY");

        assertEquals(0, parts.size());
        verify(partCacheLoader, never()).loadHashes(any(), anyString(), any());
    }

    @Test
    void 列表查询_Redis异常_自动降级Oracle宽行查询() {
        when(partCacheLoader.loadCategoryIds("CAT")).thenThrow(new RuntimeException("redis连接失败"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("WTPARTNUMBER", "P1");
        row.put("NAME", "部件1");
        row.put("OID", "OID-1");
        row.put("APPLICATIONID", "APP-1");
        when(pbiPartMapper.getPartsByClassification(eq("CAT"), anyString())).thenReturn(List.of(row));

        List<PbiPart> parts = service.getPartsByClassification("一级分类", "CAT");

        assertEquals(1, parts.size());
        assertEquals("P1", parts.get(0).getWtPartNumber());
    }

    @Test
    void 列表查询_缓存总开关关闭_直接走Oracle宽行() {
        properties.setCacheEnabled(false);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("WTPARTNUMBER", "P1");
        row.put("NAME", "部件1");
        when(pbiPartMapper.getPartsByClassification(eq("CAT"), anyString())).thenReturn(List.of(row));

        List<PbiPart> parts = service.getPartsByClassification("一级分类", "CAT");

        assertEquals(1, parts.size());
        verifyNoInteractions(partCacheRepository);
        verify(partCacheLoader, never()).loadCategoryIds(anyString());
    }

    @Test
    void 下拉选项_实时窄查询不经过缓存() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("WTPARTNUMBER", "P1");
        row.put("NAME", "部件1");
        when(pbiPartMapper.findOptionsByClassification("CAT")).thenReturn(List.of(row));

        List<String> options = service.getPartOptions("一级分类", "CAT");

        assertEquals(List.of("P1,部件1"), options);
        verifyNoInteractions(partCacheRepository);
        verifyNoInteractions(partCacheLoader);
        verify(pbiPartMapper, never()).getPartsByClassification(anyString(), anyString());
    }

    @Test
    void 详情查询_编号实时定位ID后走属性Hash() {
        when(partCacheLoader.loadIdByNumber("P1")).thenReturn("10");
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        hashes.put("10", fullHash("10", "P1"));
        when(partCacheLoader.loadHashes(eq(List.of("10")), eq("part:10"), any())).thenReturn(hashes);

        PbiPart part = service.getPartDetail("一级分类", " P1 ");

        assertEquals("P1", part.getWtPartNumber());
        assertEquals("5kW", part.getAttrs().get("POWER"));
        verify(partCacheLoader).loadIdByNumber("P1");
        verify(pbiPartMapper, never()).getPartByNumber(anyString(), anyString());
    }

    @Test
    void 详情查询_编号查无记录_返回null() {
        when(partCacheLoader.loadIdByNumber("P404")).thenReturn(null);

        PbiPart part = service.getPartDetail("一级分类", "P404");

        assertNull(part);
        verify(partCacheLoader, never()).loadHashes(any(), anyString(), any());
    }

    @Test
    void 清空缓存_委托仓储执行() {
        when(partCacheRepository.clearAll()).thenReturn(42L);

        assertEquals(42L, service.refreshPartCache());
    }
}
