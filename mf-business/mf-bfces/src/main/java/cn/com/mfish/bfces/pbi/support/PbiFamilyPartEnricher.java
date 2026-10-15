package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiShipment;
import cn.com.mfish.bfces.pbi.mapper.PbiShipmentMapper;
import cn.com.mfish.bfces.pbi.util.PbiCollectionUtils;
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
 * DOC/DPF/SCR_ASC规格已随部件快照从Redis Hash（BFCES_PBI_PART_VIEW）加载，
 * 本回填器只负责实际发货量：接口库 V_WINDCHILL_SHIPMENT 按部件编号关联，
 * 跨库查询失败时降级留空，不阻断家谱主体。采用批量IN查询避免N+1，结果原地回填到节点信息Map。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilyPartEnricher {

    private final PbiShipmentMapper pbiShipmentMapper;

    /**
     * 为家谱可见节点批量回填实际发货量（原地修改节点信息Map）。
     *
     * @param visiblePartIds 可见部件ID列表（按家谱递归发现顺序）
     * @param nodeInfo       部件ID -> 部件信息（shipment 原地回填）
     */
    public void enrich(List<String> visiblePartIds, Map<String, PbiFamilyTreeNode> nodeInfo) {
        if (visiblePartIds == null || visiblePartIds.isEmpty()) {
            return;
        }
        // 实际发货量来自独立接口库（按部件编号关联），接口库不可用时仅留空不阻断家谱主体
        fillShipments(visiblePartIds, nodeInfo);
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
            PbiCollectionUtils.putFirstNonBlank(shipmentByItem, item, value,
                    (key, existing, incoming) -> log.warn(
                            "部件编号[{}]的实际发货量存在多个不同值[{}]/[{}]，沿用[{}]",
                            key, existing, incoming, existing));
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
