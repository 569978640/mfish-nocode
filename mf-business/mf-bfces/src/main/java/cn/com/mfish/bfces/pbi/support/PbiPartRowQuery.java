package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.cache.PbiPartCacheLoader;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI产品型号行查询。
 * 封装产品列表与产品详情两条"实时ID索引 + Redis属性Hash缓存 + 异常降级宽行直查"链路：
 * 分类/编号到IDA2A2的ID列表实时查Oracle（可开30秒短缓存）以感知新增部件；
 * 部件属性Hash永久缓存，仅对新部件/新列做IN批量差量回源；
 * Redis链路异常或开关关闭时降级为动态列宽行直查，业务不中断。
 * 本类只返回与动态列SQL结果同构的行Map（key为大写列名），到 {@link cn.com.mfish.bfces.pbi.entity.PbiPart}
 * 的转换由 PbiPartConverter 负责。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiPartRowQuery {

    /** 查询型号时必须携带的固定列 */
    private static final List<String> REQUIRED_COLUMNS = List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID");

    private final PbiProperties pbiProperties;

    private final PbiPartCacheLoader partCacheLoader;

    private final PbiPartMapper pbiPartMapper;

    private final PbiViewColumnSelector viewColumnSelector;

    /**
     * 按二级分类查询产品型号行（列表链路）。
     *
     * @param productClassification 二级分类内部名称
     * @param paramConfigs          一级分类的特性参数配置（已完成成员权限过滤）
     * @return 行Map列表（key为大写列名，保持ID索引顺序）；无任何可查询列或分类下无部件时返回空列表
     */
    public List<Map<String, Object>> listRows(String productClassification, List<PbiParamConfig> paramConfigs) {
        // 1. 组装经白名单校验的查询列（固定列 + 一级分类配置的特性参数列）
        List<String> columnList = buildQueryColumns(paramConfigs);
        if (columnList.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 获取视图行：缓存开启走"实时ID + 属性Hash差量回源 + 内存投影"，异常或关闭时直查Oracle宽行
        if (pbiProperties.isCacheEnabled()) {
            try {
                // ID列表实时查索引视图（30秒短缓存），是感知视图新增部件的唯一入口
                List<String> partIds = partCacheLoader.loadCategoryIds(productClassification);
                if (partIds.isEmpty()) {
                    return new ArrayList<>();
                }
                Set<String> upperColumns = toUpperColumns(columnList);
                Map<String, Map<String, String>> hashes = partCacheLoader.loadHashes(
                        partIds, "category:" + productClassification, upperColumns);
                return projectRows(partIds, hashes, upperColumns);
            } catch (Exception e) {
                log.error("PBI部件Redis缓存链路异常，降级直查Oracle宽行，分类[{}]", productClassification, e);
            }
        }
        return pbiPartMapper.getPartsByClassification(productClassification, String.join(", ", columnList));
    }

    /**
     * 按部件编号查询单个产品型号行（详情链路）。
     *
     * @param wtPartNumber 已trim的部件编号（调用方保证非空白）
     * @param paramConfigs 一级分类的特性参数配置（已完成成员权限过滤）
     * @return 行Map（key为大写列名）；无任何可查询列或编号查无记录时返回null
     */
    public Map<String, Object> detailRow(String wtPartNumber, List<PbiParamConfig> paramConfigs) {
        // 特性参数列与产品列表保持一致（固定列 + 一级分类配置列）
        List<String> columnList = buildQueryColumns(paramConfigs);
        if (columnList.isEmpty()) {
            return null;
        }
        Set<String> upperColumns = toUpperColumns(columnList);

        if (pbiProperties.isCacheEnabled()) {
            try {
                // 编号->ID实时查索引视图（30秒短缓存），再按ID取永久属性Hash（缺部件/缺列自动差量回源）
                String partId = partCacheLoader.loadIdByNumber(wtPartNumber);
                if (StringUtils.isBlank(partId)) {
                    log.warn("部件编号[{}]在产品视图中未查询到记录", wtPartNumber);
                    return null;
                }
                Map<String, Map<String, String>> hashes = partCacheLoader.loadHashes(
                        List.of(partId), "part:" + partId, upperColumns);
                List<Map<String, Object>> rows = projectRows(List.of(partId), hashes, upperColumns);
                if (rows.isEmpty()) {
                    log.warn("部件编号[{}]对应部件[{}]缓存与回源均无数据", wtPartNumber, partId);
                    return null;
                }
                return rows.get(0);
            } catch (Exception e) {
                log.error("PBI部件详情Redis缓存链路异常，降级直查Oracle，编号[{}]", wtPartNumber, e);
            }
        }
        return loadDetailFromDb(wtPartNumber, columnList);
    }

    /**
     * 组装产品型号查询列：固定列在前，追加一级分类配置的特性参数列，经视图列白名单校验。
     *
     * @param paramConfigs 一级分类的特性参数配置
     * @return 可直接拼入SQL的列名列表；无任何可查询列时返回空列表
     */
    private List<String> buildQueryColumns(List<PbiParamConfig> paramConfigs) {
        List<String> candidates = new ArrayList<>(REQUIRED_COLUMNS);
        if (paramConfigs != null) {
            for (PbiParamConfig config : paramConfigs) {
                candidates.add(config.getColumnName());
            }
        }
        List<String> columnList = viewColumnSelector.buildQueryColumns(candidates);
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在任何可查询的列，终止本次型号查询", PbiViewColumnSelector.PBI_PART_VIEW_NAME);
        }
        return columnList;
    }

    /**
     * 按实时ID顺序从属性Hash中投影出所需列，构造与原动态列SQL结果同构的行Map。
     * 输出key统一大写列名（Oracle未加引号标识符原本即返回大写），值为字符串，交转换器按原逻辑处理。
     *
     * @param partIds 有序部件ID列表
     * @param hashes  部件ID -> 属性Map
     * @param columns 需要投影的大写列名集合（保持顺序）
     * @return 行列表；属性Hash整体缺失的异常部件告警跳过
     */
    private List<Map<String, Object>> projectRows(List<String> partIds,
                                                  Map<String, Map<String, String>> hashes,
                                                  Set<String> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String partId : partIds) {
            Map<String, String> hash = hashes.get(partId);
            if (hash == null || hash.isEmpty()) {
                log.warn("部件[{}]属性数据缺失，已从本次型号结果中跳过", partId);
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : columns) {
                row.put(column, hash.get(column));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 缓存降级/关闭时的部件详情直查逻辑：动态列宽行查询，编号唯一取首行。
     *
     * @param wtPartNumber 部件编号
     * @param columnList   经白名单校验的查询列
     * @return 行Map；未查到时返回null
     */
    private Map<String, Object> loadDetailFromDb(String wtPartNumber, List<String> columnList) {
        List<Map<String, Object>> rows = pbiPartMapper.getPartByNumber(
                wtPartNumber, String.join(", ", columnList));
        if (rows == null || rows.isEmpty()) {
            log.warn("部件编号[{}]在产品视图中未查询到记录", wtPartNumber);
            return null;
        }
        // 部件编号全库唯一，正常仅一行；出现多行时取首个并告警
        if (rows.size() > 1) {
            log.warn("部件编号[{}]在产品视图中存在[{}]行记录，详情取首行", wtPartNumber, rows.size());
        }
        return rows.get(0);
    }

    /**
     * 将列名列表统一转为大写并去重（保持顺序），用于缓存Hash field匹配与内存投影。
     *
     * @param columnList 经白名单校验的列名列表
     * @return 大写列名有序集合
     */
    private Set<String> toUpperColumns(List<String> columnList) {
        Set<String> columns = new LinkedHashSet<>();
        for (String column : columnList) {
            columns.add(column.toUpperCase());
        }
        return columns;
    }
}
