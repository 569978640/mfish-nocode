package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.client.PbiApiClient;
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
import java.util.List;
import java.util.Map;

/**
 * @description: PBI服务实现，仅负责分类节点、筛选属性、特征参数配置与产品型号查询的业务编排。
 * 视图列白名单组装委托 {@link PbiViewColumnSelector}，
 * 行数据转换与链接拼接委托 {@link PbiPartConverter}，
 * 产品家谱的递归组装与节点数据回填委托 {@link PbiFamilyTreeBuilder}，
 * 远程Windchill调用统一委托 {@link PbiApiClient}。
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.7.0
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

        // 2. 查询视图并转换为产品型号对象
        List<Map<String, Object>> rows = pbiPartMapper.getPartsByClassification(
                productClassification, String.join(", ", columnList));
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
        List<Map<String, Object>> rows = pbiPartMapper.getPartByNumber(
                wtPartNumber.trim(), String.join(", ", columnList));
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
        // 下拉选项只需编号、名称两列，经视图列存在性校验后查询，避免ORA-00904
        List<String> columnList = viewColumnSelector.buildQueryColumns(OPTION_COLUMNS);
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在编号或名称列，终止下拉选项查询", PbiViewColumnSelector.PBI_PART_VIEW_NAME);
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = pbiPartMapper.getPartsByClassification(
                productClassification, String.join(", ", columnList));
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
}
