package cn.com.mfish.bfces.pbi.service.impl;

import cn.com.mfish.bfces.pbi.client.PbiApiClient;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.mapper.PbiParamConfigMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import cn.com.mfish.bfces.pbi.service.PbiService;
import cn.com.mfish.bfces.pbi.support.PbiPartConverter;
import cn.com.mfish.bfces.pbi.support.PbiViewColumnSelector;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description: PBI服务实现，仅负责分类节点、筛选属性、特征参数配置与产品型号查询的业务编排。
 * 视图列白名单组装委托 {@link PbiViewColumnSelector}，
 * 行数据转换与链接拼接委托 {@link PbiPartConverter}，
 * 远程Windchill调用统一委托 {@link PbiApiClient}。
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.2.0
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
        LambdaQueryWrapper<PbiParamConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PbiParamConfig::getFirstCategory, firstCategory);
        queryWrapper.likeRight(PbiParamConfig::getInternalName, INTERNAL_NAME_PREFIX);
        return pbiParamConfigMapper.selectList(queryWrapper);
    }

    @Override
    public List<PbiPart> getPartsByClassification(String firstCategory, String productClassification) {
        // 1. 获取配置的列，与固定列合并为候选列（固定列在前）
        List<PbiParamConfig> paramConfigs = getParamConfig(firstCategory);
        List<String> candidates = new ArrayList<>(REQUIRED_COLUMNS);
        if (paramConfigs != null) {
            for (PbiParamConfig config : paramConfigs) {
                candidates.add(config.getColumnName());
            }
        }

        // 2. 经白名单校验组装查询列，没有任何可查询的列时直接返回，避免拼出非法SQL
        List<String> columnList = viewColumnSelector.buildQueryColumns(candidates);
        if (columnList.isEmpty()) {
            log.error("视图{}中不存在任何可查询的列，终止本次型号查询", PbiViewColumnSelector.PBI_PART_VIEW_NAME);
            return new ArrayList<>();
        }

        // 3. 查询视图并转换为产品型号对象
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
}
