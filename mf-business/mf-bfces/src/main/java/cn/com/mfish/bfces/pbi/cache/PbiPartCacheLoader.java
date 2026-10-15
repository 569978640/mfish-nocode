package cn.com.mfish.bfces.pbi.cache;

import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.util.PbiCollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI部件缓存回源加载器。
 * 在"实时ID列表"之上保证部件属性Hash齐全，两类差量只回源一次IN查询：
 * 1.缺部件（整个Hash不存在，即视图新增加的部件）：按ID批量SELECT *全列回源，视图加列自动带出；
 * 2.缺列（Hash中缺某个所需field，即视图新增属性列尚未同步）：汇总所有缺失列一次SELECT指定列回源，
 * 空值写空串作为"已同步"标记，避免对空列反复回源。
 * 同一lockKey（分类/部件维度）的并发回源由本地互斥锁收敛为单次查询（防缓存击穿）。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiPartCacheLoader {

    private final PbiPartMapper pbiPartMapper;

    private final PbiPartCacheRepository cacheRepository;

    private final PbiProperties pbiProperties;

    /** 固定条纹回源锁：按lockKey分配，避免含部件ID的锁键无界增长 */
    private final PbiLoadLocks loadLocks;

    /**
     * 获取分类下的部件ID列表：优先读30秒短缓存，未命中时实时查索引视图单列并回填短缓存。
     * 产品列表与产品家谱共用同一套"分类→ID"实时入口。
     *
     * @param productClassification 二级分类内部名称
     * @return 去空白、去重并保持视图顺序的部件ID列表
     */
    public List<String> loadCategoryIds(String productClassification) {
        List<String> cachedIds = cacheRepository.getCategoryIds(productClassification);
        if (cachedIds != null) {
            return cachedIds;
        }
        List<String> dbIds = PbiCollectionUtils.distinctTrimmed(
                pbiPartMapper.findIdsByClassification(productClassification));
        cacheRepository.putCategoryIds(productClassification, dbIds, pbiProperties.getIdCacheTtlSeconds());
        return dbIds;
    }

    /**
     * 获取单个部件编号对应的部件ID：优先读短缓存（含查无标记），未命中时实时查索引视图并回填。
     *
     * @param wtPartNumber 已trim的部件编号
     * @return 部件ID；视图查无记录时返回null
     */
    public String loadIdByNumber(String wtPartNumber) {
        String cachedId = cacheRepository.getIdByNumber(wtPartNumber);
        if (cachedId != null) {
            return StringUtils.trimToNull(cachedId);
        }
        List<String> ids = pbiPartMapper.findIdsByNumber(wtPartNumber);
        String partId = firstNotBlank(ids);
        cacheRepository.putIdByNumber(wtPartNumber, partId, pbiProperties.getIdCacheTtlSeconds());
        return partId;
    }

    /**
     * 确保给定部件的缓存Hash包含全部所需列，返回部件ID -> 属性Map（field全大写）。
     * 返回Map中个别部件可能为空/缺列（回源仍查不到的异常数据），由调用方记录并跳过。
     *
     * @param partIds         实时查到的部件ID列表（顺序即展示顺序）
     * @param lockKey         回源互斥锁标识（列表用"category:{分类}"，详情用"part:{ID}"）
     * @param requiredColumns 本次查询需要的列名集合（大写）
     * @return 部件ID -> 属性Map（field全大写）
     */
    public Map<String, Map<String, String>> loadHashes(List<String> partIds, String lockKey,
                                                       Set<String> requiredColumns) {
        if (partIds == null || partIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 第一次读缓存：全部部件Hash都含全部所需列时直接返回，无锁快路径
        Map<String, Map<String, String>> hashes = cacheRepository.getPartHashes(partIds);
        if (PbiCollectionUtils.allFieldsReady(hashes, requiredColumns)) {
            return hashes;
        }
        // 加锁后双重检查，避免新部件/新列首次出现时并发重复回源
        synchronized (loadLocks.lockOf(lockKey)) {
            hashes = cacheRepository.getPartHashes(partIds);
            fillMissingParts(hashes);
            fillMissingColumns(hashes, requiredColumns);
            return hashes;
        }
    }

    /**
     * 回填缓存中整体缺失的部件Hash（视图新增部件）：一次IN批量SELECT *全列查询并写缓存。
     *
     * @param hashes 部件ID -> 属性Map（原地补全）
     */
    private void fillMissingParts(Map<String, Map<String, String>> hashes) {
        List<String> missingIds = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : hashes.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                missingIds.add(entry.getKey());
            }
        }
        if (missingIds.isEmpty()) {
            return;
        }
        log.info("PBI部件缓存未命中部件{}个，执行全列批量回源:{}", missingIds.size(), missingIds);
        List<Map<String, Object>> rows = pbiPartMapper.findAllByIds(missingIds);
        // 全列行写永久缓存（SELECT *自动覆盖视图未来新增列）
        cacheRepository.savePartRows(rows);
        Set<String> loadedIds = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, String> normalized = PbiPartCacheRepository.normalizeRow(row);
                String partId = normalized.get(PbiPartCacheRepository.PART_ID_COLUMN);
                if (partId != null) {
                    hashes.put(partId, normalized);
                    loadedIds.add(partId);
                }
            }
        }
        for (String missingId : missingIds) {
            if (!loadedIds.contains(missingId)) {
                // ID来自同视图实时查询却查不到全列行，属异常数据：保留空Map，由调用方跳过并告警
                log.warn("部件[{}]全列回源未返回数据，可能已从视图移除或存在数据异常", missingId);
                hashes.put(missingId, Collections.emptyMap());
            }
        }
    }

    /**
     * 回填已有部件Hash中缺失的列（视图新增属性列首次被请求）：
     * 汇总"列->缺失该列的部件"后，用一次IN + 指定列查询完成全部补齐。
     *
     * @param hashes          部件ID -> 属性Map（原地补全）
     * @param requiredColumns 所需列集合（大写）
     */
    private void fillMissingColumns(Map<String, Map<String, String>> hashes, Set<String> requiredColumns) {
        // 列名 -> 缺该列的部件ID（保持顺序，便于SQL与日志稳定）
        Map<String, List<String>> missingColumnToIds = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : hashes.entrySet()) {
            Map<String, String> hash = entry.getValue();
            if (hash == null || hash.isEmpty()) {
                continue;
            }
            for (String column : requiredColumns) {
                if (!hash.containsKey(column)) {
                    missingColumnToIds.computeIfAbsent(column, key -> new ArrayList<>()).add(entry.getKey());
                }
            }
        }
        if (missingColumnToIds.isEmpty()) {
            return;
        }
        List<String> missingColumns = new ArrayList<>(missingColumnToIds.keySet());
        Set<String> involvedIds = new LinkedHashSet<>();
        for (List<String> ids : missingColumnToIds.values()) {
            involvedIds.addAll(ids);
        }
        log.info("PBI部件缓存检测到新增列{}，涉及部件{}个，执行指定列批量回源", missingColumns, involvedIds.size());
        List<Map<String, Object>> rows = pbiPartMapper.findColumnsByIds(
                new ArrayList<>(involvedIds), String.join(", ", missingColumns));

        // 按"部件×列"的精确缺失关系组装写入内容，并原地合并内存结果
        Map<String, Map<String, String>> fieldsToSave = new LinkedHashMap<>();
        Set<String> returnedRows = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, String> normalized = PbiPartCacheRepository.normalizeRow(row);
                String partId = normalized.get(PbiPartCacheRepository.PART_ID_COLUMN);
                if (partId == null) {
                    continue;
                }
                returnedRows.add(partId);
                Map<String, String> hash = hashes.get(partId);
                Map<String, String> fields = fieldsToSave.computeIfAbsent(partId, key -> new LinkedHashMap<>());
                for (String column : missingColumns) {
                    List<String> missingPartIds = missingColumnToIds.get(column);
                    if (missingPartIds != null && missingPartIds.contains(partId)) {
                        // 查不到的列归一化后为空串，同样写入：空串field即"该列已同步"标记
                        String value = normalized.getOrDefault(column, "");
                        fields.put(column, value);
                        if (hash != null) {
                            hash.put(column, value);
                        }
                    }
                }
            }
        }
        cacheRepository.savePartFields(fieldsToSave);
        // IN查询未返回的部件：缺失列补空串占位，避免后续请求反复回源，同时记录异常日志
        for (List<String> ids : missingColumnToIds.values()) {
            for (String partId : ids) {
                if (returnedRows.contains(partId)) {
                    continue;
                }
                log.warn("部件[{}]缺列回源未返回数据，缺失列以空串占位", partId);
                Map<String, String> hash = hashes.get(partId);
                Map<String, String> fields = fieldsToSave.computeIfAbsent(partId, key -> new LinkedHashMap<>());
                for (Map.Entry<String, List<String>> columnEntry : missingColumnToIds.entrySet()) {
                    if (columnEntry.getValue().contains(partId)) {
                        fields.putIfAbsent(columnEntry.getKey(), "");
                        if (hash != null) {
                            hash.putIfAbsent(columnEntry.getKey(), "");
                        }
                    }
                }
            }
        }
    }

    /**
     * 取列表中首个非空白值并trim。
     *
     * @param values 原始值列表
     * @return 首个非空白值；全部空白或列表为空时返回null
     */
    private String firstNotBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
