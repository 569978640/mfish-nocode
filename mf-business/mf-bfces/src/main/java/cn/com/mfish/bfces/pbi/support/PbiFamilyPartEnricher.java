package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiPartSpec;
import cn.com.mfish.bfces.pbi.entity.PbiShipment;
import cn.com.mfish.bfces.pbi.mapper.PbiFamilyMapper;
import cn.com.mfish.bfces.pbi.mapper.PbiShipmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI产品家谱节点数据回填器。
 * 负责为家谱可见节点批量回填两类展示数据，不参与家谱拓扑关系的计算：
 * 1.DOC/DPF/SCR_ASC规格软属性（Windchill主库 STRINGVALUE.VALUE2）；
 * 2.实际发货量（接口库 V_WINDCHILL_SHIPMENT，按部件编号关联，跨库查询失败时降级留空）。
 * 两类数据均采用批量IN查询避免N+1，结果原地回填到节点信息Map。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilyPartEnricher {

    /** DOC规格软属性内部名 */
    private static final String DOC_ATTR_NAME = "com.bfces.DOC";

    /** DPF规格软属性内部名 */
    private static final String DPF_ATTR_NAME = "com.bfces.DPF";

    /** SCR规格软属性内部名（前端SCR标签取SCR_ASC定义） */
    private static final String SCR_ATTR_NAME = "com.bfces.SCR_ASC";

    /** 家谱卡片展示的规格软属性内部名集合：DOC/DPF/SCR（SCR取SCR_ASC定义） */
    private static final List<String> SPEC_ATTR_NAMES = List.of(DOC_ATTR_NAME, DPF_ATTR_NAME, SCR_ATTR_NAME);

    private final PbiFamilyMapper pbiFamilyMapper;

    /** 接口库发货量Mapper（@DS("interface") 走独立Oracle数据源） */
    private final PbiShipmentMapper pbiShipmentMapper;

    /**
     * 为家谱可见节点批量回填规格软属性与实际发货量（原地修改节点信息Map）。
     *
     * @param visiblePartIds 可见部件ID列表（按家谱递归发现顺序）
     * @param nodeInfo       部件ID -> 部件信息（doc/dpf/scrAsc/shipment 原地回填）
     */
    public void enrich(List<String> visiblePartIds, Map<String, PbiFamilyTreeNode> nodeInfo) {
        if (visiblePartIds == null || visiblePartIds.isEmpty()) {
            return;
        }
        fillPartSpecs(visiblePartIds, nodeInfo);
        // 实际发货量来自独立接口库（按部件编号关联），接口库不可用时仅留空不阻断家谱主体
        fillShipments(visiblePartIds, nodeInfo);
    }

    /**
     * 批量查询部件的DOC/DPF/SCR_ASC规格软属性（STRINGVALUE.VALUE2）并回填到部件信息。
     * 一次IN查询取回全部部件×全部属性的取值；同一部件同一属性出现多行时保留首个非空值并告警；
     * 空白值归一为null，使前端对应标签不渲染。
     *
     * @param partIds  可见部件ID集合
     * @param nodeInfo 部件ID -> 部件信息（原地回填规格字段）
     */
    private void fillPartSpecs(List<String> partIds, Map<String, PbiFamilyTreeNode> nodeInfo) {
        List<PbiPartSpec> specRows = pbiFamilyMapper.findSpecValues(partIds, SPEC_ATTR_NAMES);
        if (specRows == null || specRows.isEmpty()) {
            return;
        }
        // partId -> (attrName -> value)，按部件归并三个属性的取值
        Map<String, Map<String, String>> specMap = new HashMap<>();
        for (PbiPartSpec row : specRows) {
            if (row == null || StringUtils.isBlank(row.getPartId()) || StringUtils.isBlank(row.getAttrName())) {
                continue;
            }
            String value = StringUtils.trimToNull(row.getSpecValue());
            Map<String, String> attrMap = specMap.computeIfAbsent(row.getPartId(), key -> new HashMap<>());
            String existing = attrMap.get(row.getAttrName());
            if (existing == null) {
                if (value != null) {
                    attrMap.put(row.getAttrName(), value);
                }
            } else if (value != null && !existing.equals(value)) {
                // 正常同一部件同一软属性只有一行，出现多个不同值时沿用首个并告警
                log.warn("部件[{}]的规格属性[{}]存在多个不同值[{}]/[{}]，沿用[{}]",
                        row.getPartId(), row.getAttrName(), existing, value, existing);
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : specMap.entrySet()) {
            PbiFamilyTreeNode info = nodeInfo.get(entry.getKey());
            if (info == null) {
                continue;
            }
            Map<String, String> attrMap = entry.getValue();
            info.setDoc(attrMap.get(DOC_ATTR_NAME));
            info.setDpf(attrMap.get(DPF_ATTR_NAME));
            info.setScrAsc(attrMap.get(SCR_ATTR_NAME));
        }
    }

    /**
     * 批量查询可见节点的实际发货量（接口库 V_WINDCHILL_SHIPMENT 视图，按部件编号 ITEM 关联）并回填。
     * 发货量数据源为另一台Oracle服务器，查询异常时仅记录错误日志、发货量留null（前端展示0），不阻断家谱主体；
     * 同一编号在视图中出现多行时沿用首个非空值并告警；空白值归一为null。
     *
     * @param partIds  可见部件ID集合
     * @param nodeInfo 部件ID -> 部件信息（原地回填发货量字段）
     */
    private void fillShipments(List<String> partIds, Map<String, PbiFamilyTreeNode> nodeInfo) {
        // 发货量视图按部件编号关联（而非部件ID），收集可见节点编号：去空白、去重、保持节点顺序
        List<String> partNumbers = new ArrayList<>();
        Set<String> numberSet = new HashSet<>();
        for (String partId : partIds) {
            PbiFamilyTreeNode info = nodeInfo.get(partId);
            if (info == null || StringUtils.isBlank(info.getWtPartNumber())) {
                continue;
            }
            String partNumber = info.getWtPartNumber().trim();
            if (numberSet.add(partNumber)) {
                partNumbers.add(partNumber);
            }
        }
        if (partNumbers.isEmpty()) {
            return;
        }
        List<PbiShipment> shipmentRows;
        try {
            shipmentRows = pbiShipmentMapper.findShipments(partNumbers);
        } catch (Exception e) {
            // 接口库为跨服务器依赖，连接/查询失败时降级：家谱正常返回，发货量统一留空由前端展示0
            log.error("查询接口库实际发货量失败，本次家谱发货量留空，部件编号{}", partNumbers, e);
            return;
        }
        if (shipmentRows == null || shipmentRows.isEmpty()) {
            return;
        }
        // 部件编号 -> 发货量，视图正常一编号一行，多行时保留首个非空值
        Map<String, String> shipmentByItem = new HashMap<>();
        for (PbiShipment row : shipmentRows) {
            if (row == null || StringUtils.isBlank(row.getItem())) {
                continue;
            }
            String item = row.getItem().trim();
            String value = StringUtils.trimToNull(row.getShipment());
            String existing = shipmentByItem.get(item);
            if (existing == null) {
                if (value != null) {
                    shipmentByItem.put(item, value);
                }
            } else if (value != null && !existing.equals(value)) {
                log.warn("部件编号[{}]的实际发货量存在多个不同值[{}]/[{}]，沿用[{}]",
                        item, existing, value, existing);
            }
        }
        if (shipmentByItem.isEmpty()) {
            return;
        }
        for (String partId : partIds) {
            PbiFamilyTreeNode info = nodeInfo.get(partId);
            if (info == null || StringUtils.isBlank(info.getWtPartNumber())) {
                continue;
            }
            // 视图无记录的编号 get 返回 null，前端按 0 展示
            info.setShipment(shipmentByItem.get(info.getWtPartNumber().trim()));
        }
    }
}
