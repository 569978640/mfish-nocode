package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.cache.PbiPartCacheLoader;
import cn.com.mfish.bfces.pbi.cache.PbiPartCacheRepository;
import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.config.PbiProperties;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.service.PbiService;
import cn.com.mfish.bfces.pbi.support.PbiFamilyTreeBuilder;
import cn.com.mfish.bfces.pbi.support.PbiPartConverter;
import cn.com.mfish.bfces.pbi.support.PbiViewColumnSelector;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI服务实现，仅负责分类节点、筛选属性、特征参数配置与产品型号查询的业务编排。
 * 部件型号查询采用"实时ID索引 + Redis属性Hash缓存"两层模型：
 * 分类/编号到IDA2A2的ID列表实时查Oracle（可开30秒短缓存）以感知新增部件；
 * 部件属性Hash（pbi:part:detail:{IDA2A2}）永久缓存，仅对新部件/新列做IN批量差量回源；
 * Redis链路异常时自动降级为原有动态列宽行直查，业务不中断。
 * 视图列白名单组装委托 {@link PbiViewColumnSelector}，
 * 行数据转换与链接拼接委托 {@link PbiPartConverter}，
 * 产品家谱的递归组装与节点数据回填委托 {@link PbiFamilyTreeBuilder}，
 * 远程Windchill调用统一委托 {@link PbiApiClient}。
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PbiServiceImpl implements PbiService {

    /** 一级分类根节点名称 */
    private static final String ROOT_PARENT_NODE = "BFCES_ProductClassification";

    /** "ALL"兜底节点名称 */
    private static final String ALL_NODE = "ALL";

    /** 参数内部名称前缀，仅查询bfces自定义属性 */
    private static final String INTERNAL_NAME_PREFIX = "com.bfces.";

    /** 查询型号时必须携带的固定列 */
    private static final List<String> REQUIRED_COLUMNS = List.of("OID", "WTPARTNUMBER", "NAME", "APPLICATIONID");

    /** 下拉选项只需编号、名称两列 */
    private static final List<String> OPTION_COLUMNS = List.of("WTPARTNUMBER", "NAME");

    private final PbiApiClient pbiApiClient;

    private final PbiParamConfigMapper pbiParamConfigMapper;

    private final PbiPartMapper pbiPartMapper;

    private final PbiViewColumnSelector viewColumnSelector;

    private final PbiPartConverter partConverter;

    /** 产品家谱构建器：自底向上批量递归、剪枝与可见性计算、树DTO组装 */
    private final PbiFamilyTreeBuilder familyTreeBuilder;

    /** PBI接口与缓存配置 */
    private final PbiProperties pbiProperties;

    /** 部件属性Redis缓存仓储（Hash/索引读写、缓存清空） */
    private final PbiPartCacheRepository partCacheRepository;

    /** 部件缓存差量回源加载器（新部件全列、新列补齐、防击穿） */
    private final PbiPartCacheLoader partCacheLoader;

    @Override
    public List<PbiNode> findNode1() {
        List<PbiNode> nodes = pbiApiClient.findChildNodes(ROOT_PARENT_NODE);
        // 末尾追加ALL兜底节点，供前端选择全部分类
        PbiNode allNode = new PbiNode();
        allNode.setName(ALL_NODE);
        allNode.setDisplayName(ALL_NODE);
        allNode.setDescription(ALL_NODE);
        nodes.add(allNode);
        return nodes;
    }

    @Override
    public List<PbiNode> findNode2(String parentNodeName) {
        return pbiApiClient.findChildNodes(parentNodeName);
    }

    @Override
    public List<PbiParamConfig> getParamConfig(String firstCategory) {
        String account = AuthInfoUtils.getCurrentAccount();
        LambdaQueryWrapper<PbiParamConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PbiParamConfig::getFirstCategory, firstCategory);
        queryWrapper.likeRight(PbiParamConfig::getInternalName, INTERNAL_NAME_PREFIX);
        // member为逗号分隔的多账号串：值为空（NULL或纯空白，Oracle中空串等同NULL）视为不限制成员，对所有人（含未登录场景）可见；
        // 非空时字段值与账号均首尾补逗号后做整词匹配（INSTR定位），
        // 避免LIKE通配符问题及账号互为子串的误匹配（如账号san不应命中zhangsan）；
        // 未获取到登录账号时仅保留空member分支，配置了成员的行不会因账号缺失而放行
        queryWrapper.and(wrapper -> {
            wrapper.apply("TRIM(MEMBER) IS NULL");
            if (StringUtils.isNotBlank(account)) {
                wrapper.or().apply("INSTR(',' || MEMBER || ',', ',' || {0} || ',') > 0", account);
            }
        });
        return pbiParamConfigMapper.selectList(queryWrapper);
    }

    @Override
    public List<PbiPart> getPartsByClassification(String firstCategory, String productClassification) {
        // 1. 组装经白名单校验的查询列（固定列 + 一级分类配置的特性参数列）
        List<String> columnList = buildPartQueryColumns(firstCategory);
        if (columnList.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 获取视图行：缓存开启走"实时ID + 属性Hash差量回源 + 内存投影"，异常或关闭时直查Oracle宽行
        List<Map<String, Object>> rows;
        if (pbiProperties.isCacheEnabled()) {
            try {
                // ID列表实时查库（可开30秒短缓存），是感知视图新增部件的唯一入口
                List<String> partIds = resolveCategoryPartIds(productClassification);
                if (partIds.isEmpty()) {
                    return new ArrayList<>();
                }
                Set<String> upperColumns = toUpperColumns(columnList);
                Map<String, Map<String, String>> hashes = partCacheLoader.loadHashes(
                        partIds, "category:" + productClassification, upperColumns);
                rows = projectRows(partIds, hashes, upperColumns);
            } catch (Exception e) {
                log.error("PBI部件Redis缓存链路异常，降级直查Oracle宽行，分类[{}]", productClassification, e);
                rows = pbiPartMapper.getPartsByClassification(
                        productClassification, String.join(", ", columnList));
            }
        } else {
            rows = pbiPartMapper.getPartsByClassification(
                    productClassification, String.join(", ", columnList));
        }

        // 3. 转换为产品型号对象（转换逻辑与缓存改造前完全一致）
        List<PbiPart> parts = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return parts;
        }
        for (Map<String, Object> row : rows) {
            parts.add(partConverter.toPart(row));
        }
        return parts;
    }

    @Override
    public PbiPart getPartDetail(String firstCategory, String wtPartNumber) {
        if (StringUtils.isBlank(wtPartNumber)) {
            log.warn("部件编号为空，终止产品详情查询");
            return null;
        }
        // 特性参数列与产品列表保持一致（固定列 + 一级分类配置列）
        List<String> columnList = buildPartQueryColumns(firstCategory);
        if (columnList.isEmpty()) {
            return null;
        }
        String partNumber = wtPartNumber.trim();
        Set<String> upperColumns = toUpperColumns(columnList);

        if (pbiProperties.isCacheEnabled()) {
            try {
                // 编号->ID实时查库（可开30秒短缓存），再按ID取永久属性Hash（缺部件/缺列自动差量回源）
                String partId = resolvePartId(partNumber);
                if (StringUtils.isBlank(partId)) {
                    log.warn("部件编号[{}]在产品视图中未查询到记录", partNumber);
                    return null;
                }
                Map<String, Map<String, String>> hashes = partCacheLoader.loadHashes(
                        List.of(partId), "part:" + partId, upperColumns);
                List<Map<String, Object>> rows = projectRows(List.of(partId), hashes, upperColumns);
                if (rows.isEmpty()) {
                    log.warn("部件编号[{}]对应部件[{}]缓存与回源均无数据", partNumber, partId);
                    return null;
                }
                return partConverter.toPart(rows.get(0));
            } catch (Exception e) {
                log.error("PBI部件详情Redis缓存链路异常，降级直查Oracle，编号[{}]", partNumber, e);
            }
        }
        return loadDetailFromDb(partNumber, columnList);
    }

    @Override
    public long refreshPartCache() {
        // 应急运维：清空全部PBI部件缓存（Hash与ID索引），后续请求自动按实时ID从Oracle差量重建
        return partCacheRepository.clearAll();
    }

    /**
     * 组装产品型号查询列：固定列在前，追加一级分类配置的特性参数列，经视图列白名单校验。
     *
     * @param firstCategory 一级分类
     * @return 可直接拼入SQL的列名列表；无任何可查询列时返回空列表
     */
    private List<String> buildPartQueryColumns(String firstCategory) {
        List<PbiParamConfig> paramConfigs = getParamConfig(firstCategory);
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

    @Override
    public List<String> getPartOptions(String firstCategory, String productClassification) {
        // 下拉选项只需编号、名称两列，经视图列存在性校验后做实时窄查询（不走缓存），保证新增部件即时可见
        List<String> columnList = viewColumnSelector.buildQueryColumns(OPTION_COLUMNS);
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在编号或名称列，终止下拉选项查询", PbiViewColumnSelector.PBI_PART_VIEW_NAME);
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = pbiPartMapper.findOptionsByClassification(productClassification);
        List<String> options = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return options;
        }
        // 每项格式为"部件编号,部件名称"；编号为空的脏数据由转换器返回null，此处跳过
        for (Map<String, Object> row : rows) {
            String option = partConverter.toOptionLabel(row);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }

    @Override
    public List<PbiFilterProperty> getFilterProperties(String nodeName) {
        return pbiApiClient.getAttrs(nodeName);
    }

    @Override
    public PbiFamilyTreeNode getFamilyTree(String productClassification) {
        // 家谱构建为独立编排（递归查询、剪枝、可见性、规格/发货量回填、树组装），整体委托构建器
        return familyTreeBuilder.build(productClassification);
    }

    /**
     * 获取分类下的部件ID列表：优先读短缓存，未命中时实时查Oracle单列并回填短缓存。
     * 空分类同样缓存空列表，防缓存穿透；Redis异常向上抛出由调用方统一降级。
     *
     * @param productClassification 二级分类内部名称
     * @return 去空白、去重并保持视图顺序的部件ID列表
     */
    private List<String> resolveCategoryPartIds(String productClassification) {
        List<String> cachedIds = partCacheRepository.getCategoryIds(productClassification);
        if (cachedIds != null) {
            return cachedIds;
        }
        List<String> dbIds = distinctTrimmed(pbiPartMapper.findIdsByClassification(productClassification));
        partCacheRepository.putCategoryIds(productClassification, dbIds, pbiProperties.getIdCacheTtlSeconds());
        return dbIds;
    }

    /**
     * 获取部件编号对应的部件ID：优先读短缓存（含查无标记），未命中时实时查Oracle并回填短缓存。
     *
     * @param wtPartNumber 已trim的部件编号
     * @return 部件ID；视图查无记录时返回null
     */
    private String resolvePartId(String wtPartNumber) {
        String cachedId = partCacheRepository.getIdByNumber(wtPartNumber);
        if (cachedId != null) {
            return StringUtils.trimToNull(cachedId);
        }
        List<String> ids = pbiPartMapper.findIdsByNumber(wtPartNumber);
        String partId = null;
        if (ids != null) {
            for (String id : ids) {
                if (StringUtils.isNotBlank(id)) {
                    partId = id.trim();
                    break;
                }
            }
        }
        partCacheRepository.putIdByNumber(wtPartNumber, partId, pbiProperties.getIdCacheTtlSeconds());
        return partId;
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
     * @return 产品型号；未查到时返回null
     */
    private PbiPart loadDetailFromDb(String wtPartNumber, List<String> columnList) {
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
        return partConverter.toPart(rows.get(0));
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

    /**
     * ID列表归一化：去空白、trim、按出现顺序去重。
     *
     * @param rawIds 数据库返回的原始ID列表
     * @return 归一化后的ID列表
     */
    private List<String> distinctTrimmed(List<String> rawIds) {
        List<String> result = new ArrayList<>();
        if (rawIds == null) {
            return result;
        }
        Set<String> exists = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            if (StringUtils.isNotBlank(rawId) && exists.add(rawId.trim())) {
                result.add(rawId.trim());
            }
        }
        return result;
    }
}
