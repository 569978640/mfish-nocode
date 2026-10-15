package cn.com.mfish.bfces.pbi.cache;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @description: PBI部件缓存差量回源加载器单元测试：覆盖全命中快路径、新部件全列回源、新列批量补齐、空值占位，
 * 以及分类/编号ID索引的短缓存与差量回源。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.1.0
 */
@ExtendWith(MockitoExtension.class)
class PbiPartCacheLoaderTest {

    @Mock
    private PbiPartMapper pbiPartMapper;

    @Mock
    private PbiPartCacheRepository cacheRepository;

    private PbiProperties properties;

    private PbiPartCacheLoader loader;

    private Set<String> requiredColumns;

    @BeforeEach
    void setUp() {
        properties = new PbiProperties();
        // 回源锁使用真实条纹锁组件（无外部依赖），锁内双检行为与生产一致
        loader = new PbiPartCacheLoader(pbiPartMapper, cacheRepository, properties, new PbiLoadLocks());
        requiredColumns = new LinkedHashSet<>(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID"));
    }

    /** 构造包含全部所需列的完整部件Hash */
    private Map<String, String> fullHash(String partId) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(PbiPartCacheRepository.PART_ID_COLUMN, partId);
        hash.put("OID", "OID-" + partId);
        hash.put("WTPARTNUMBER", "P" + partId);
        hash.put("NAME", "部件" + partId);
        hash.put("APPLICATIONID", "APP-" + partId);
        return hash;
    }

    @Test
    void loadHashes_全部缓存命中_不触发任何回源() {
        List<String> ids = List.of("1", "2");
        Map<String, Map<String, String>> cached = new LinkedHashMap<>();
        cached.put("1", fullHash("1"));
        cached.put("2", fullHash("2"));
        when(cacheRepository.getPartHashes(ids)).thenReturn(cached);

        Map<String, Map<String, String>> result = loader.loadHashes(ids, "category:X", requiredColumns);

        assertEquals(2, result.size());
        assertEquals("P1", result.get("1").get("WTPARTNUMBER"));
        verify(pbiPartMapper, never()).findAllByIds(any());
        verify(pbiPartMapper, never()).findColumnsByIds(any(), anyString());
        verify(cacheRepository, never()).savePartRows(any());
        verify(cacheRepository, never()).savePartFields(any());
    }

    @Test
    void loadHashes_存在新部件_全列批量回源并合并() {
        List<String> ids = List.of("1", "2");
        // 锁外快路径与锁内双重检查各读一次，两次结果一致：部件2的Hash整体缺失
        Map<String, Map<String, String>> cached = new LinkedHashMap<>();
        cached.put("1", fullHash("1"));
        cached.put("2", new LinkedHashMap<>());
        when(cacheRepository.getPartHashes(ids)).thenReturn(cached);

        Map<String, Object> newRow = new LinkedHashMap<>();
        newRow.put("IDA2A2", "2");
        newRow.put("OID", "OID-2");
        newRow.put("WTPARTNUMBER", "P2");
        newRow.put("NAME", "部件2");
        newRow.put("APPLICATIONID", "APP-2");
        when(pbiPartMapper.findAllByIds(List.of("2"))).thenReturn(List.of(newRow));

        Map<String, Map<String, String>> result = loader.loadHashes(ids, "category:X", requiredColumns);

        verify(pbiPartMapper).findAllByIds(List.of("2"));
        verify(cacheRepository).savePartRows(List.of(newRow));
        verify(pbiPartMapper, never()).findColumnsByIds(any(), anyString());
        assertEquals("P2", result.get("2").get("WTPARTNUMBER"));
        assertEquals("OID-2", result.get("2").get("OID"));
    }

    @Test
    void loadHashes_视图新增列_一次指定列批量补齐() {
        requiredColumns = new LinkedHashSet<>(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "POWER"));
        List<String> ids = List.of("1");
        // 已缓存Hash中没有POWER field（视图新加列、老缓存尚未同步）
        Map<String, Map<String, String>> cached = new LinkedHashMap<>();
        cached.put("1", fullHash("1"));
        when(cacheRepository.getPartHashes(ids)).thenReturn(cached);

        Map<String, Object> columnRow = new LinkedHashMap<>();
        columnRow.put("IDA2A2", "1");
        columnRow.put("POWER", "5kW");
        when(pbiPartMapper.findColumnsByIds(eq(List.of("1")), anyString())).thenReturn(List.of(columnRow));

        Map<String, Map<String, String>> result = loader.loadHashes(ids, "category:X", requiredColumns);

        verify(pbiPartMapper, never()).findAllByIds(any());
        ArgumentCaptor<Map<String, Map<String, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cacheRepository).savePartFields(captor.capture());
        assertEquals("5kW", captor.getValue().get("1").get("POWER"));
        assertEquals("5kW", result.get("1").get("POWER"));
    }

    @Test
    void loadHashes_新列值为空_写空串作为已同步标记() {
        requiredColumns = new LinkedHashSet<>(List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID", "POWER"));
        List<String> ids = List.of("1");
        Map<String, Map<String, String>> cached = new LinkedHashMap<>();
        cached.put("1", fullHash("1"));
        when(cacheRepository.getPartHashes(ids)).thenReturn(cached);

        // 驱动对NULL列可能不返回entry，等价于该列无值
        Map<String, Object> columnRow = new LinkedHashMap<>();
        columnRow.put("IDA2A2", "1");
        when(pbiPartMapper.findColumnsByIds(eq(List.of("1")), anyString())).thenReturn(List.of(columnRow));

        Map<String, Map<String, String>> result = loader.loadHashes(ids, "category:X", requiredColumns);

        ArgumentCaptor<Map<String, Map<String, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cacheRepository).savePartFields(captor.capture());
        assertEquals("", captor.getValue().get("1").get("POWER"));
        assertEquals("", result.get("1").get("POWER"));
        assertTrue(result.get("1").containsKey("POWER"));
    }

    @Test
    void 分类ID短缓存命中_直接返回不查索引视图() {
        when(cacheRepository.getCategoryIds("CAT")).thenReturn(List.of("1", "2"));

        List<String> ids = loader.loadCategoryIds("CAT");

        assertEquals(List.of("1", "2"), ids);
        verify(pbiPartMapper, never()).findIdsByClassification(anyString());
        verify(cacheRepository, never()).putCategoryIds(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 分类ID短缓存未命中_查索引视图去重trim并回填30秒() {
        when(cacheRepository.getCategoryIds("CAT")).thenReturn(null);
        when(pbiPartMapper.findIdsByClassification("CAT")).thenReturn(List.of("1", " 2 ", "1", " "));

        List<String> ids = loader.loadCategoryIds("CAT");

        assertEquals(List.of("1", "2"), ids);
        verify(cacheRepository).putCategoryIds("CAT", List.of("1", "2"), 30L);
    }

    @Test
    void 编号ID短缓存命中查无标记_返回null不查库() {
        when(cacheRepository.getIdByNumber("P404")).thenReturn("");

        assertNull(loader.loadIdByNumber("P404"));
        verify(pbiPartMapper, never()).findIdsByNumber(anyString());
    }
}
