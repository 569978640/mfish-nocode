package cn.com.mfish.bfces.pbi.cache;

import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 回源锁池：key为分类标识（列表查询）或部件标识（详情查询），数量有限常驻内存 */
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

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
        if (isComplete(hashes, requiredColumns)) {
            return hashes;
        }
        // 加锁后双重检查，避免新部件/新列首次出现时并发重复回源
        Object lock = loadLocks.computeIfAbsent(lockKey, key -> new Object());
        synchronized (lock) {
            hashes = cacheRepository.getPartHashes(partIds);
            fillMissingParts(hashes);
            fillMissingColumns(hashes, requiredColumns);
            return hashes;
        }
    }

    /**
     * 判断所有部件Hash是否都存在且包含全部所需列。
     *
     * @param hashes          部件ID -> 属性Map
     * @param requiredColumns 所需列集合（大写）
     * @return true表示缓存完整，无需回源
     */
    private boolean isComplete(Map<String, Map<String, String>> hashes, Set<String> requiredColumns) {
        for (Map<String, String> hash : hashes.values()) {
            if (hash == null || hash.isEmpty()) {
                return false;
            }
            for (String column : requiredColumns) {
                if (!hash.containsKey(column)) {
                    return false;
                }
            }
        }
        return true;
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
}
