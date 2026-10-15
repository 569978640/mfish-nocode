package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiPartSpec;
import cn.com.mfish.bfces.pbi.entity.PbiPreviousMaterial;
import cn.com.mfish.bfces.pbi.mapper.PbiFamilyMapper;
import cn.com.mfish.bfces.pbi.util.PbiCollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * @description: PBI产品家谱部件原始表回源查询。
 * 仅负责与Windchill原始表打交道的三类批量IN查询，不感知Redis缓存：
 * 1.WTPART关联WTPARTMASTER：按ID取编号/状态骨架；
 * 2.STRINGVALUE(com.bfces.PreviousMaterialCode)：按ID取上一代物料编码；
 * 3.STRINGVALUE(com.bfces.DOC/DPF/SCR_ASC)：按ID取三规格。
 * 同一部件同字段出现多行时保留首个非空值，值冲突时告警。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilyPartQuery {

    /** 规格Hash字段 -> STRINGDEFINITION.NAME 内部名 */
    private static final Map<String, String> SPEC_ATTR_NAME_BY_FIELD = Map.of(
            PbiFamilySnapshotAssembler.FIELD_DOC, "com.bfces.DOC",
            PbiFamilySnapshotAssembler.FIELD_DPF, "com.bfces.DPF",
            PbiFamilySnapshotAssembler.FIELD_SCR, "com.bfces.SCR_ASC");

    private final PbiFamilyMapper pbiFamilyMapper;

    /**
     * 按ID批量查WTPART关联WTPARTMASTER，组装编号/状态骨架。
     *
     * @param partIds 部件ID集合
     * @return 部件ID -> [编号, 状态]；入参为空时返回空Map
     */
    public Map<String, String[]> findBaseInfo(List<String> partIds) {
        Map<String, String[]> result = new LinkedHashMap<>();
        if (partIds.isEmpty()) {
            return result;
        }
        List<PbiFamilyTreeNode> infos = pbiFamilyMapper.findPartInfoByIds(partIds);
        if (infos != null) {
            for (PbiFamilyTreeNode info : infos) {
                if (info == null || StringUtils.isBlank(info.getPartId())) {
                    continue;
                }
                result.put(info.getPartId().trim(), new String[]{
                        StringUtils.trimToEmpty(info.getWtPartNumber()),
                        StringUtils.trimToEmpty(info.getState())});
            }
        }
        return result;
    }

    /**
     * 批量查"上一代物料编码"软属性：同部件多行保留首个非空值并告警。
     *
     * @param partIds 部件ID集合
     * @return 部件ID -> 上一代部件编号/BASELINE（空白值不放入Map，语义等同"支系剪枝"）
     */
    public Map<String, String> findPrevious(List<String> partIds) {
        Map<String, String> result = new LinkedHashMap<>();
        if (partIds.isEmpty()) {
            return result;
        }
        List<PbiPreviousMaterial> rows = pbiFamilyMapper.findPreviousMaterials(partIds);
        if (rows == null) {
            return result;
        }
        for (PbiPreviousMaterial row : rows) {
            if (row == null || StringUtils.isBlank(row.getPartId())) {
                continue;
            }
            String partId = row.getPartId().trim();
            String value = StringUtils.trimToNull(row.getPreviousValue());
            PbiCollectionUtils.putFirstNonBlank(result, partId, value,
                    (key, existing, incoming) -> log.warn(
                            "部件[{}]的上一代物料编码存在多个不同值[{}]/[{}]，沿用[{}]",
                            key, existing, incoming, existing));
        }
        return result;
    }

    /**
     * 批量查DOC/DPF/SCR_ASC规格软属性：同部件同属性多行保留首个非空值并告警。
     *
     * @param partIds 部件ID集合
     * @return 部件ID -> (规格Hash字段名 -> 值)
     */
    public Map<String, Map<String, String>> findSpecs(List<String> partIds) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (partIds.isEmpty()) {
            return result;
        }
        List<PbiPartSpec> rows = pbiFamilyMapper.findSpecValues(partIds, specAttrNames());
        if (rows == null) {
            return result;
        }
        for (PbiPartSpec row : rows) {
            if (row == null || StringUtils.isBlank(row.getPartId()) || StringUtils.isBlank(row.getAttrName())) {
                continue;
            }
            String field = specFieldOfAttr(row.getAttrName());
            if (field == null) {
                continue;
            }
            String partId = row.getPartId().trim();
            String value = StringUtils.trimToNull(row.getSpecValue());
            Map<String, String> specMap = result.computeIfAbsent(partId, key -> new HashMap<>());
            PbiCollectionUtils.putFirstNonBlank(specMap, field, value,
                    (key, existing, incoming) -> log.warn(
                            "部件[{}]的规格属性[{}]存在多个不同值[{}]/[{}]，沿用[{}]",
                            partId, key, existing, incoming, existing));
        }
        return result;
    }

    /**
     * 规格软属性内部名列表（供Mapper IN查询，去重保序）。
     *
     * @return 属性内部名列表
     */
    private List<String> specAttrNames() {
        return new ArrayList<>(new LinkedHashSet<>(SPEC_ATTR_NAME_BY_FIELD.values()));
    }

    /**
     * 规格软属性内部名 -> Hash字段名反向映射。
     *
     * @param attrName STRINGDEFINITION.NAME
     * @return Hash字段名；非三规格属性时返回null
     */
    private String specFieldOfAttr(String attrName) {
        for (Map.Entry<String, String> entry : SPEC_ATTR_NAME_BY_FIELD.entrySet()) {
            if (entry.getValue().equals(attrName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
