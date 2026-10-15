package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.cache.PbiPartCacheRepository;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyPartSnapshot;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_DOC;
import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_DPF;
import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_NUMBER;
import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_PREVIOUS;
import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_SCR;
import static cn.com.mfish.bfces.pbi.support.PbiFamilySnapshotAssembler.FIELD_STATE;

/**
 * @description: PBI产品家谱快照字段级补全器。
 * 缓存路径下以固定骨架（父层实时查询结果，为null时表示叶层）与Hash现状确定有效部件集合，
 * 对Hash整体缺失或缺具体field的部件，分别回源WTPART（编号/状态）与STRINGVALUE（上一代编码/规格），
 * 回源结果回填Hash（空值写空串作"已同步"标记）；
 * 全降级路径（缓存关闭或Redis异常）则编号/状态查WTPART、上一代编码与规格全量查STRINGVALUE，不读写Redis。
 * 本类只做"补全算法"，缓存开关判断、快路径与异常降级由 PbiFamilyPartReader 门面负责。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilySnapshotCompleter {

    private final PbiPartCacheRepository cacheRepository;

    private final PbiFamilyPartQuery partQuery;

    private final PbiFamilySnapshotAssembler assembler;

    /**
     * 缓存路径的字段级补全核心。
     *
     * @param partIds   部件ID有序列表
     * @param fixedBase 父层固定的实时部件信息（partId -> 编号/状态）；叶层传null
     * @param hashes    Redis读出的部件Hash（空Map表示Hash整体缺失）
     * @return 部件ID -> 快照
     */
    public Map<String, PbiFamilyPartSnapshot> completeFromCache(List<String> partIds,
                                                                Map<String, PbiFamilyTreeNode> fixedBase,
                                                                Map<String, Map<String, String>> hashes) {
        boolean parentMode = fixedBase != null;

        // 1. 确定有效骨架与编号/状态：父层用实时固定值；叶层取Hash，Hash缺失或缺编号状态field时回源WTPART
        Map<String, String[]> baseInfo = new LinkedHashMap<>();
        List<String> infoQueryIds = new ArrayList<>();
        if (parentMode) {
            for (Map.Entry<String, PbiFamilyTreeNode> entry : fixedBase.entrySet()) {
                baseInfo.put(entry.getKey(), new String[]{
                        StringUtils.trimToEmpty(entry.getValue().getWtPartNumber()),
                        StringUtils.trimToEmpty(entry.getValue().getState())});
            }
        }
        for (String partId : partIds) {
            if (parentMode && baseInfo.containsKey(partId)) {
                continue;
            }
            Map<String, String> hash = hashes.get(partId);
            if (hash != null && !hash.isEmpty()
                    && hash.containsKey(FIELD_NUMBER) && hash.containsKey(FIELD_STATE)) {
                baseInfo.put(partId, new String[]{
                        StringUtils.trimToEmpty(hash.get(FIELD_NUMBER)),
                        StringUtils.trimToEmpty(hash.get(FIELD_STATE))});
            } else {
                infoQueryIds.add(partId);
            }
        }
        if (!infoQueryIds.isEmpty()) {
            Map<String, String[]> dbInfo = partQuery.findBaseInfo(infoQueryIds);
            for (String partId : infoQueryIds) {
                String[] info = dbInfo.get(partId);
                if (info != null) {
                    baseInfo.put(partId, info);
                } else {
                    Map<String, String> hash = hashes.get(partId);
                    if (hash != null && !hash.isEmpty()) {
                        // Hash曾写入过但WTPART已查无信息：边缘异常数据，沿用Hash值并告警，不轻易剪枝
                        log.warn("家谱部件[{}]在WTPART中未查到编号/状态，沿用Redis Hash现值", partId);
                        baseInfo.put(partId, new String[]{
                                StringUtils.trimToEmpty(hash.get(FIELD_NUMBER)),
                                StringUtils.trimToEmpty(hash.get(FIELD_STATE))});
                    }
                    // Hash整体缺失且WTPART无信息：不进入骨架，由构建器剪枝
                }
            }
        }

        // 2. 在骨架上收集上一代编码与规格的字段缺失，分别回源STRINGVALUE
        List<String> previousQueryIds = new ArrayList<>();
        List<String> specQueryIds = new ArrayList<>();
        for (String partId : baseInfo.keySet()) {
            Map<String, String> hash = hashes.get(partId);
            boolean hashMissing = hash == null || hash.isEmpty();
            if (hashMissing || !hash.containsKey(FIELD_PREVIOUS)) {
                previousQueryIds.add(partId);
            }
            if (hashMissing || !hash.containsKey(FIELD_DOC)
                    || !hash.containsKey(FIELD_DPF) || !hash.containsKey(FIELD_SCR)) {
                specQueryIds.add(partId);
            }
        }
        Map<String, String> previousByPart = partQuery.findPrevious(previousQueryIds);
        Map<String, Map<String, String>> specByPart = partQuery.findSpecs(specQueryIds);

        // 3. 组装快照并把本次回源字段回填Hash（空值写空串，含父层实时编号/状态）
        Map<String, PbiFamilyPartSnapshot> snapshots = new LinkedHashMap<>();
        Map<String, Map<String, String>> toSave = new LinkedHashMap<>();
        for (String partId : baseInfo.keySet()) {
            String[] info = baseInfo.get(partId);
            Map<String, String> hash = hashes.get(partId);
            Map<String, String> fields = new LinkedHashMap<>();
            String number = info[0];
            String state = info[1];
            String previous = resolveField(partId, FIELD_PREVIOUS, previousQueryIds, previousByPart, hash);
            String doc = resolveSpec(partId, FIELD_DOC, specQueryIds, specByPart, hash);
            String dpf = resolveSpec(partId, FIELD_DPF, specQueryIds, specByPart, hash);
            String scr = resolveSpec(partId, FIELD_SCR, specQueryIds, specByPart, hash);

            snapshots.put(partId, assembler.toSnapshot(partId, number, state, previous, doc, dpf, scr));

            // 仅回写本次实际回源过的字段，避免无谓写Redis；空串保留"已同步"语义
            boolean touched = false;
            if (parentMode || infoQueryIds.contains(partId)) {
                fields.put(FIELD_NUMBER, StringUtils.defaultString(number));
                fields.put(FIELD_STATE, StringUtils.defaultString(state));
                touched = true;
            }
            if (previousQueryIds.contains(partId)) {
                fields.put(FIELD_PREVIOUS, StringUtils.defaultString(previous));
                touched = true;
            }
            if (specQueryIds.contains(partId)) {
                fields.put(FIELD_DOC, StringUtils.defaultString(doc));
                fields.put(FIELD_DPF, StringUtils.defaultString(dpf));
                fields.put(FIELD_SCR, StringUtils.defaultString(scr));
                touched = true;
            }
            if (touched) {
                toSave.put(partId, fields);
            }
        }
        // 无有效骨架或本次无任何字段回源时不发起空pipeline调用
        if (!toSave.isEmpty()) {
            cacheRepository.savePartFields(toSave);
        }
        return snapshots;
    }

    /**
     * 全降级路径（缓存关闭或Redis异常）：编号/状态查WTPART（父层用固定骨架跳过），
     * 上一代编码与规格全量查STRINGVALUE，不读写Redis。
     *
     * @param partIds   部件ID有序列表
     * @param fixedBase 父层固定实时骨架；叶层传null
     * @return 部件ID -> 快照
     */
    public Map<String, PbiFamilyPartSnapshot> completeFromDb(List<String> partIds,
                                                             Map<String, PbiFamilyTreeNode> fixedBase) {
        Map<String, String[]> baseInfo = new LinkedHashMap<>();
        if (fixedBase != null) {
            for (Map.Entry<String, PbiFamilyTreeNode> entry : fixedBase.entrySet()) {
                baseInfo.put(entry.getKey(), new String[]{
                        StringUtils.trimToEmpty(entry.getValue().getWtPartNumber()),
                        StringUtils.trimToEmpty(entry.getValue().getState())});
            }
        }
        List<String> infoQueryIds = new ArrayList<>();
        for (String partId : partIds) {
            if (!baseInfo.containsKey(partId)) {
                infoQueryIds.add(partId);
            }
        }
        baseInfo.putAll(partQuery.findBaseInfo(infoQueryIds));

        Map<String, String> previousByPart = partQuery.findPrevious(new ArrayList<>(baseInfo.keySet()));
        Map<String, Map<String, String>> specByPart = partQuery.findSpecs(new ArrayList<>(baseInfo.keySet()));

        Map<String, PbiFamilyPartSnapshot> snapshots = new LinkedHashMap<>();
        for (String partId : partIds) {
            String[] info = baseInfo.get(partId);
            if (info == null) {
                continue;
            }
            Map<String, String> spec = specByPart.get(partId);
            snapshots.put(partId, assembler.toSnapshot(partId, info[0], info[1],
                    previousByPart.get(partId),
                    spec == null ? null : spec.get(FIELD_DOC),
                    spec == null ? null : spec.get(FIELD_DPF),
                    spec == null ? null : spec.get(FIELD_SCR)));
        }
        return snapshots;
    }

    /**
     * 解析单值字段：本次回源了则取回源值（无值为null），未回源取Hash现值。
     */
    private String resolveField(String partId, String field, List<String> queriedIds,
                                Map<String, String> dbValues, Map<String, String> hash) {
        if (queriedIds.contains(partId)) {
            return dbValues.get(partId);
        }
        return hash == null ? null : hash.get(field);
    }

    /**
     * 解析规格字段：本次回源了则取回源值（无值为null），未回源取Hash现值。
     */
    private String resolveSpec(String partId, String field, List<String> queriedIds,
                               Map<String, Map<String, String>> dbSpecs, Map<String, String> hash) {
        if (queriedIds.contains(partId)) {
            Map<String, String> spec = dbSpecs.get(partId);
            return spec == null ? null : spec.get(field);
        }
        return hash == null ? null : hash.get(field);
    }
}
