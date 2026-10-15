package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.cache.PbiLoadLocks;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheLoader;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheRepository;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyPartSnapshot;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.mapper.PbiFamilyMapper;
import cn.com.mfish.bfces.pbi.util.PbiCollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: PBI产品家谱部件数据读取门面，严格按以下优先级编排（具体SQL见 PbiFamilyMapper 各方法注释）：
 * 1.叶节点ID：索引视图 BFCES_PBI_PART_INDX_VIEW 实时单列窄查询（经30秒短缓存，可配置为纯实时）；
 * 2.部件编号/状态（按ID）：Redis部件Hash优先，miss回源 WTPART 关联 WTPARTMASTER 实时查询；
 * 3.上一代物料编码（按ID）：Redis Hash优先，miss回源 STRINGVALUE(com.bfces.PreviousMaterialCode) 实时查询；
 * 4.DOC/DPF/SCR_ASC规格（按ID）：Redis Hash优先，miss回源 STRINGVALUE(com.bfces.DOC/DPF/SCR_ASC) 实时查询；
 * 5.编号→父部件：始终实时执行 findLatestPartByNumbers（ROW_NUMBER取最新已发布/已作废版本），
 *   不走任何缓存，保证家谱每一层都连接到编号对应的最新版本部件。
 * 本类只负责缓存开关判断、Hash快路径、回源互斥与Redis异常降级；
 * 字段级差量补全与回填委托 {@link PbiFamilySnapshotCompleter}，
 * 原始表批量回源委托 {@link PbiFamilyPartQuery}，快照组装委托 {@link PbiFamilySnapshotAssembler}。
 * Redis整体异常时各环节自动回退到Windchill原始表查询，家谱主体不中断。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilyPartReader {

    private final PbiProperties pbiProperties;

    private final PbiPartCacheLoader partCacheLoader;

    private final PbiPartCacheRepository cacheRepository;

    private final PbiFamilyMapper pbiFamilyMapper;

    /** 固定条纹回源锁，按家谱层级lockKey分配 */
    private final PbiLoadLocks loadLocks;

    /** 字段级差量补全与Hash回填（缓存路径/全降级路径） */
    private final PbiFamilySnapshotCompleter snapshotCompleter;

    /** 快路径快照组装 */
    private final PbiFamilySnapshotAssembler assembler;

    /**
     * 查询二级分类下的底层部件ID（家谱叶节点入口）。
     *
     * @param productClassification 二级分类内部名称
     * @return 部件ID列表（去空白、去重、保持视图顺序）
     */
    public List<String> findLeafIds(String productClassification) {
        if (pbiProperties.isCacheEnabled()) {
            try {
                return partCacheLoader.loadCategoryIds(productClassification);
            } catch (Exception e) {
                log.error("家谱叶节点ID缓存链路异常，降级直查索引视图，分类[{}]", productClassification, e);
            }
        }
        return PbiCollectionUtils.distinctTrimmed(pbiFamilyMapper.findLeafPartIds(productClassification));
    }

    /**
     * 批量加载叶层部件快照（编号/状态/上一代物料编码/DOC/DPF/SCR）：
     * Redis Hash优先，Hash整体缺失或个别字段缺失时分别回源WTPART/STRINGVALUE原始表并回填Hash。
     *
     * @param partIds 叶层部件ID集合
     * @param lockKey 回源互斥锁标识
     * @return 部件ID -> 快照（保持入参顺序）；WTPART中查无信息的异常部件不放入结果（构建器按缺失剪枝）
     */
    public Map<String, PbiFamilyPartSnapshot> loadSnapshots(List<String> partIds, String lockKey) {
        if (partIds == null || partIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (pbiProperties.isCacheEnabled()) {
            try {
                // 快路径：全部部件Hash含全部家谱字段，直接转换无回源
                Map<String, Map<String, String>> hashes = cacheRepository.getPartHashes(partIds);
                if (PbiCollectionUtils.allFieldsReady(
                        hashes, PbiFamilySnapshotAssembler.ALL_FIELDS)) {
                    return toSnapshotsFast(partIds, hashes, null);
                }
                // 锁内双重检查后执行字段级回源：无固定骨架，编号/状态也由Hash或WTPART回源提供
                synchronized (loadLocks.lockOf(lockKey)) {
                    return snapshotCompleter.completeFromCache(
                            partIds, null, cacheRepository.getPartHashes(partIds));
                }
            } catch (Exception e) {
                log.error("家谱叶层快照Redis链路异常，降级直查WTPART/STRINGVALUE，部件数[{}]", partIds.size(), e);
            }
        }
        return snapshotCompleter.completeFromDb(partIds, null);
    }

    /**
     * 按父部件编号批量解析父部件快照（家谱每层递归调用）。
     * 编号→部件始终实时执行 findLatestPartByNumbers（最新已发布/已作废版本），state/编号以实时结果为准；
     * 上一代编码与DOC/DPF/SCR规格仍走Redis Hash优先、miss回源STRINGVALUE。
     *
     * @param wtPartNumbers 父部件编号集合（"上一代物料编码"值）
     * @param lockKey       回源互斥锁标识
     * @return 编号 -> 父部件快照（保持入参顺序）；编号未解析到有效部件时不放入结果（构建器剪枝）
     */
    public Map<String, PbiFamilyPartSnapshot> findParentSnapshots(List<String> wtPartNumbers, String lockKey) {
        if (wtPartNumbers == null || wtPartNumbers.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 编号→最新版本部件：实时查WTPART，任何情况下都不走缓存
        List<PbiFamilyTreeNode> parents = pbiFamilyMapper.findLatestPartByNumbers(wtPartNumbers);
        Map<String, PbiFamilyTreeNode> fixedBase = new LinkedHashMap<>();
        List<String> parentIds = new ArrayList<>();
        if (parents != null) {
            for (PbiFamilyTreeNode parent : parents) {
                if (parent == null || StringUtils.isBlank(parent.getPartId())
                        || StringUtils.isBlank(parent.getWtPartNumber())) {
                    continue;
                }
                String partId = parent.getPartId().trim();
                fixedBase.put(partId, parent);
                parentIds.add(partId);
            }
        }
        if (parentIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, PbiFamilyPartSnapshot> snapshotsById;
        if (pbiProperties.isCacheEnabled()) {
            try {
                Map<String, Map<String, String>> hashes = cacheRepository.getPartHashes(parentIds);
                // 父层快路径：state/编号已实时获得，仅要求上一代编码与三规格字段齐全
                if (PbiCollectionUtils.allFieldsReady(
                        hashes, PbiFamilySnapshotAssembler.PARENT_REQUIRED_FIELDS)) {
                    snapshotsById = toSnapshotsFast(parentIds, hashes, fixedBase);
                } else {
                    synchronized (loadLocks.lockOf(lockKey)) {
                        snapshotsById = snapshotCompleter.completeFromCache(
                                parentIds, fixedBase, cacheRepository.getPartHashes(parentIds));
                    }
                }
            } catch (Exception e) {
                log.error("家谱父层快照Redis链路异常，上一代编码与规格降级直查STRINGVALUE，部件数[{}]",
                        parentIds.size(), e);
                snapshotsById = snapshotCompleter.completeFromDb(parentIds, fixedBase);
            }
        } else {
            snapshotsById = snapshotCompleter.completeFromDb(parentIds, fixedBase);
        }

        // 以"编号"为key返回，供构建器关联子部件；实时结果保证编号非空
        Map<String, PbiFamilyPartSnapshot> result = new LinkedHashMap<>();
        for (PbiFamilyTreeNode parent : fixedBase.values()) {
            PbiFamilyPartSnapshot snapshot = snapshotsById.get(parent.getPartId().trim());
            if (snapshot != null) {
                result.put(parent.getWtPartNumber().trim(), snapshot);
            }
        }
        return result;
    }

    /**
     * 快路径转换：全部部件Hash有效且含所需字段时直接组装快照。
     *
     * @param partIds   部件ID有序列表
     * @param hashes    Redis Hash结果
     * @param fixedBase 父层固定实时骨架（编号/状态），叶层为null
     * @return 部件ID -> 快照
     */
    private Map<String, PbiFamilyPartSnapshot> toSnapshotsFast(List<String> partIds,
                                                               Map<String, Map<String, String>> hashes,
                                                               Map<String, PbiFamilyTreeNode> fixedBase) {
        Map<String, PbiFamilyPartSnapshot> snapshots = new LinkedHashMap<>();
        for (String partId : partIds) {
            Map<String, String> hash = hashes.get(partId);
            if (hash == null || hash.isEmpty()) {
                log.warn("家谱部件[{}]属性Hash缺失，已从本次家谱结果中跳过", partId);
                continue;
            }
            snapshots.put(partId, assembler.toSnapshotFromHash(partId, hash, fixedBase));
        }
        return snapshots;
    }
}
