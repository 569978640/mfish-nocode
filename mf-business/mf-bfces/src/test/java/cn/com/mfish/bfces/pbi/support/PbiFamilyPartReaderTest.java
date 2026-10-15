package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.cache.PbiLoadLocks;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheLoader;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheRepository;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyPartSnapshot;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiPartSpec;
import cn.com.mfish.bfces.pbi.entity.PbiPreviousMaterial;
import cn.com.mfish.bfces.pbi.mapper.PbiFamilyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * @description: PBI产品家谱部件读取器单元测试。
 * 覆盖：叶ID索引视图入口、Hash全命中快路径、缺部件/缺字段的原始表miss回源与回填、
 * 父编号始终实时查最新版本、Redis异常与缓存关闭时的WTPART/STRINGVALUE降级。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.1.0
 */
@ExtendWith(MockitoExtension.class)
class PbiFamilyPartReaderTest {

    @Mock
    private PbiPartCacheLoader partCacheLoader;
    @Mock
    private PbiPartCacheRepository cacheRepository;
    @Mock
    private PbiFamilyMapper pbiFamilyMapper;

    private PbiProperties properties;
    private PbiFamilyPartReader reader;

    @BeforeEach
    void setUp() {
        properties = new PbiProperties();
        // 组装真实的家谱读取链路（门面+补全器+原始表查询+组装器），mock仅停在Mapper/Redis边界，锁定对外行为
        PbiFamilySnapshotAssembler assembler = new PbiFamilySnapshotAssembler();
        PbiFamilyPartQuery familyPartQuery = new PbiFamilyPartQuery(pbiFamilyMapper);
        PbiFamilySnapshotCompleter completer =
                new PbiFamilySnapshotCompleter(cacheRepository, familyPartQuery, assembler);
        reader = new PbiFamilyPartReader(properties, partCacheLoader, cacheRepository, pbiFamilyMapper,
                new PbiLoadLocks(), completer, assembler);
    }

    /** 构造家谱六字段齐全的属性Hash */
    private static Map<String, String> familyHash(String partId, String number, String previous) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("IDA2A2", partId);
        hash.put("WTPARTNUMBER", number);
        hash.put("STATE", "RELEASED");
        hash.put("PREVIOUSMATERIALCODE", previous);
        hash.put("DOC", "DOC-" + number);
        hash.put("DPF", "");
        hash.put("SCR_ASC", " SCR-" + number + " ");
        return hash;
    }

    private static PbiFamilyTreeNode node(String partId, String number, String state) {
        PbiFamilyTreeNode node = new PbiFamilyTreeNode();
        node.setPartId(partId);
        node.setWtPartNumber(number);
        node.setState(state);
        return node;
    }

    @Test
    void 叶节点ID_缓存开启_委托加载器读索引视图短缓存() {
        when(partCacheLoader.loadCategoryIds("CAT")).thenReturn(List.of("1", "2"));

        assertEquals(List.of("1", "2"), reader.findLeafIds("CAT"));
        verify(pbiFamilyMapper, never()).findLeafPartIds(anyString());
    }

    @Test
    void 叶节点ID_缓存关闭_直接查索引视图并去重trim() {
        properties.setCacheEnabled(false);
        when(pbiFamilyMapper.findLeafPartIds("CAT")).thenReturn(List.of("1", " 2 ", "1"));

        assertEquals(List.of("1", "2"), reader.findLeafIds("CAT"));
    }

    @Test
    void 叶节点ID_Redis异常_降级索引视图直查() {
        when(partCacheLoader.loadCategoryIds("CAT")).thenThrow(new RuntimeException("redis连接失败"));
        when(pbiFamilyMapper.findLeafPartIds("CAT")).thenReturn(List.of("1"));

        assertEquals(List.of("1"), reader.findLeafIds("CAT"));
    }

    @Test
    void 叶快照_Hash六字段齐全_直接转换不回源不回写() {
        List<String> ids = List.of("1");
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        hashes.put("1", familyHash("1", "P1", "BASELINE"));
        when(cacheRepository.getPartHashes(ids)).thenReturn(hashes);

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        PbiFamilyPartSnapshot snapshot = snapshots.get("1");
        assertEquals("P1", snapshot.getWtPartNumber());
        assertEquals("RELEASED", snapshot.getState());
        assertEquals("BASELINE", snapshot.getPreviousMaterialCode());
        assertEquals("DOC-P1", snapshot.getDoc());
        assertNull(snapshot.getDpf());
        assertEquals("SCR-P1", snapshot.getScrAsc());
        verify(pbiFamilyMapper, never()).findPartInfoByIds(any());
        verify(pbiFamilyMapper, never()).findPreviousMaterials(any());
        verify(pbiFamilyMapper, never()).findSpecValues(any(), any());
        verify(cacheRepository, never()).savePartFields(any());
    }

    @Test
    void 叶快照_Hash整体缺失_回源WTPART与STRINGVALUE三类查询并回填Hash() {
        List<String> ids = List.of("1");
        when(cacheRepository.getPartHashes(ids)).thenAnswer(invocation -> Map.of("1", new LinkedHashMap<>()));
        when(pbiFamilyMapper.findPartInfoByIds(ids)).thenReturn(List.of(node("1", "P1", "RELEASED")));
        PbiPreviousMaterial previous = new PbiPreviousMaterial();
        previous.setPartId("1");
        previous.setPreviousValue("BASELINE");
        when(pbiFamilyMapper.findPreviousMaterials(ids)).thenReturn(List.of(previous));
        PbiPartSpec doc = new PbiPartSpec();
        doc.setPartId("1");
        doc.setAttrName("com.bfces.DOC");
        doc.setSpecValue("DOC-X");
        when(pbiFamilyMapper.findSpecValues(eq(ids), any())).thenReturn(List.of(doc));

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        PbiFamilyPartSnapshot snapshot = snapshots.get("1");
        assertEquals("P1", snapshot.getWtPartNumber());
        assertEquals("BASELINE", snapshot.getPreviousMaterialCode());
        assertEquals("DOC-X", snapshot.getDoc());
        // 六字段均回写Hash，未查到的DPF/SCR写空串作已同步标记
        ArgumentCaptor<Map<String, Map<String, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cacheRepository).savePartFields(captor.capture());
        Map<String, String> saved = captor.getValue().get("1");
        assertEquals("P1", saved.get("WTPARTNUMBER"));
        assertEquals("RELEASED", saved.get("STATE"));
        assertEquals("BASELINE", saved.get("PREVIOUSMATERIALCODE"));
        assertEquals("DOC-X", saved.get("DOC"));
        assertEquals("", saved.get("DPF"));
        assertEquals("", saved.get("SCR_ASC"));
    }

    @Test
    void 叶快照_仅缺上一代编码字段_只回源STRINGVALUE不查WTPART与规格() {
        List<String> ids = List.of("1");
        Map<String, String> hash = familyHash("1", "P1", null);
        hash.remove("PREVIOUSMATERIALCODE");
        when(cacheRepository.getPartHashes(ids)).thenAnswer(invocation -> Map.of("1", hash));
        PbiPreviousMaterial previous = new PbiPreviousMaterial();
        previous.setPartId("1");
        previous.setPreviousValue("P0");
        when(pbiFamilyMapper.findPreviousMaterials(ids)).thenReturn(List.of(previous));

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        assertEquals("P0", snapshots.get("1").getPreviousMaterialCode());
        verify(pbiFamilyMapper, never()).findPartInfoByIds(any());
        verify(pbiFamilyMapper, never()).findSpecValues(any(), any());
    }

    @Test
    void 叶快照_Hash缺失且WTPART查无信息_部件不进入结果() {
        List<String> ids = List.of("404");
        when(cacheRepository.getPartHashes(ids)).thenAnswer(invocation -> Map.of("404", new LinkedHashMap<>()));
        when(pbiFamilyMapper.findPartInfoByIds(ids)).thenReturn(new ArrayList<>());

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        assertEquals(0, snapshots.size());
        verify(pbiFamilyMapper, never()).findPreviousMaterials(any());
        verify(cacheRepository, never()).savePartFields(any());
    }

    @Test
    void 父部件解析_编号始终实时查最新版本_状态以实时结果为准() {
        List<String> numbers = List.of("P9");
        when(pbiFamilyMapper.findLatestPartByNumbers(numbers))
                .thenReturn(List.of(node("90", "P9", "OBSOLETE")));
        // 父Hash四字段（上一代编码+规格）齐全，state/编号不再依赖Hash
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("PREVIOUSMATERIALCODE", "BASELINE");
        hash.put("DOC", "");
        hash.put("DPF", "");
        hash.put("SCR_ASC", "");
        hash.put("STATE", "RELEASED");
        when(cacheRepository.getPartHashes(List.of("90")))
                .thenReturn(new LinkedHashMap<>(Map.of("90", hash)));

        Map<String, PbiFamilyPartSnapshot> parents = reader.findParentSnapshots(numbers, "family:parents:CAT");

        // 缓存开启仍实时查最新版本WTPART，且不走findPartInfoByIds
        verify(pbiFamilyMapper).findLatestPartByNumbers(numbers);
        verify(pbiFamilyMapper, never()).findPartInfoByIds(any());
        PbiFamilyPartSnapshot snapshot = parents.get("P9");
        assertEquals("90", snapshot.getPartId());
        assertEquals("OBSOLETE", snapshot.getState());
        assertEquals("BASELINE", snapshot.getPreviousMaterialCode());
    }

    @Test
    void 父部件解析_父Hash缺规格字段_实时版本查询后回源STRINGVALUE补齐() {
        List<String> numbers = List.of("P9");
        when(pbiFamilyMapper.findLatestPartByNumbers(numbers))
                .thenReturn(List.of(node("90", "P9", "RELEASED")));
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("PREVIOUSMATERIALCODE", "BASELINE");
        // 缺DOC/DPF/SCR_ASC
        when(cacheRepository.getPartHashes(List.of("90")))
                .thenReturn(new LinkedHashMap<>(Map.of("90", hash)),
                        new LinkedHashMap<>(Map.of("90", hash)));
        PbiPartSpec dpf = new PbiPartSpec();
        dpf.setPartId("90");
        dpf.setAttrName("com.bfces.DPF");
        dpf.setSpecValue("DPF-Y");
        when(pbiFamilyMapper.findSpecValues(eq(List.of("90")), any())).thenReturn(List.of(dpf));

        Map<String, PbiFamilyPartSnapshot> parents = reader.findParentSnapshots(numbers, "family:parents:CAT");

        assertEquals("DPF-Y", parents.get("P9").getDpf());
        // 回写包含实时编号/状态（父层固定值）与规格补齐值
        ArgumentCaptor<Map<String, Map<String, String>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cacheRepository).savePartFields(captor.capture());
        Map<String, String> saved = captor.getValue().get("90");
        assertEquals("P9", saved.get("WTPARTNUMBER"));
        assertEquals("RELEASED", saved.get("STATE"));
        assertEquals("DPF-Y", saved.get("DPF"));
        assertEquals("", saved.get("DOC"));
    }

    @Test
    void 父部件解析_编号未解析到有效版本_返回空Map() {
        when(pbiFamilyMapper.findLatestPartByNumbers(List.of("P_MISS"))).thenReturn(new ArrayList<>());

        Map<String, PbiFamilyPartSnapshot> parents =
                reader.findParentSnapshots(List.of("P_MISS"), "family:parents:CAT");

        assertEquals(0, parents.size());
        verify(cacheRepository, never()).getPartHashes(any());
    }

    @Test
    void 叶快照_Redis异常_降级WTPART与STRINGVALUE全量直查() {
        List<String> ids = List.of("1");
        when(cacheRepository.getPartHashes(ids)).thenThrow(new RuntimeException("redis连接失败"));
        when(pbiFamilyMapper.findPartInfoByIds(ids)).thenReturn(List.of(node("1", "P1", "RELEASED")));
        PbiPreviousMaterial previous = new PbiPreviousMaterial();
        previous.setPartId("1");
        previous.setPreviousValue("BASELINE");
        when(pbiFamilyMapper.findPreviousMaterials(ids)).thenReturn(List.of(previous));
        when(pbiFamilyMapper.findSpecValues(eq(ids), any())).thenReturn(new ArrayList<>());

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        assertEquals("BASELINE", snapshots.get("1").getPreviousMaterialCode());
        verify(cacheRepository, never()).savePartFields(any());
    }

    @Test
    void 缓存关闭_叶层直查三类原始表且不触碰Redis() {
        properties.setCacheEnabled(false);
        List<String> ids = List.of("1");
        when(pbiFamilyMapper.findPartInfoByIds(ids)).thenReturn(List.of(node("1", "P1", "RELEASED")));
        PbiPreviousMaterial previous = new PbiPreviousMaterial();
        previous.setPartId("1");
        previous.setPreviousValue("BASELINE");
        when(pbiFamilyMapper.findPreviousMaterials(ids)).thenReturn(List.of(previous));
        when(pbiFamilyMapper.findSpecValues(eq(ids), any())).thenReturn(new ArrayList<>());

        Map<String, PbiFamilyPartSnapshot> snapshots = reader.loadSnapshots(ids, "family:leaf:CAT");

        assertEquals("P1", snapshots.get("1").getWtPartNumber());
        assertEquals("BASELINE", snapshots.get("1").getPreviousMaterialCode());
        verifyNoInteractions(cacheRepository);
    }

    @Test
    void 缓存关闭_父层实时最新版本叠加STRINGVALUE上一代编码与规格() {
        properties.setCacheEnabled(false);
        List<String> numbers = List.of("P9");
        when(pbiFamilyMapper.findLatestPartByNumbers(numbers))
                .thenReturn(List.of(node("90", "P9", "RELEASED")));
        PbiPreviousMaterial previous = new PbiPreviousMaterial();
        previous.setPartId("90");
        previous.setPreviousValue("BASELINE");
        when(pbiFamilyMapper.findPreviousMaterials(List.of("90"))).thenReturn(List.of(previous));
        when(pbiFamilyMapper.findSpecValues(eq(List.of("90")), any())).thenReturn(new ArrayList<>());

        Map<String, PbiFamilyPartSnapshot> parents = reader.findParentSnapshots(numbers, "family:parents:CAT");

        assertEquals("BASELINE", parents.get("P9").getPreviousMaterialCode());
        // 编号/状态已由实时最新版本查询提供，不再按ID查WTPART基础信息
        verify(pbiFamilyMapper, never()).findPartInfoByIds(any());
        verifyNoInteractions(cacheRepository);
    }
}
