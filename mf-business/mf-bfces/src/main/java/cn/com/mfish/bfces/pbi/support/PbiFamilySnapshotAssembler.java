package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyPartSnapshot;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @description: PBI产品家谱快照组装器。
 * 统一家谱六字段（编号/状态/上一代物料编码/DOC/DPF/SCR_ASC）的Hash字段名定义、
 * 缓存快路径的快照组装、补全结果组装，以及快照到家谱节点展示信息的拷贝，
 * 消除读取器、补全器、树构建器之间重复的字段常量与new/set样板。
 * 本类无状态，仅做对象组装，不发起任何查询或缓存读写。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Component
public class PbiFamilySnapshotAssembler {

    /** Hash字段：部件编号 */
    public static final String FIELD_NUMBER = "WTPARTNUMBER";

    /** Hash字段：生命周期状态 */
    public static final String FIELD_STATE = "STATE";

    /** Hash字段：上一代物料编码 */
    public static final String FIELD_PREVIOUS = "PREVIOUSMATERIALCODE";

    /** Hash字段：DOC规格 */
    public static final String FIELD_DOC = "DOC";

    /** Hash字段：DPF规格 */
    public static final String FIELD_DPF = "DPF";

    /** Hash字段：SCR规格（取SCR_ASC定义） */
    public static final String FIELD_SCR = "SCR_ASC";

    /** 叶层快路径依赖的全部Hash字段 */
    public static final List<String> ALL_FIELDS =
            List.of(FIELD_NUMBER, FIELD_STATE, FIELD_PREVIOUS, FIELD_DOC, FIELD_DPF, FIELD_SCR);

    /** 父层快路径依赖的Hash字段（编号/状态由实时版本查询提供，仅需上一代编码与三规格） */
    public static final List<String> PARENT_REQUIRED_FIELDS =
            List.of(FIELD_PREVIOUS, FIELD_DOC, FIELD_DPF, FIELD_SCR);

    /**
     * 按家谱六字段组装部件快照，各值统一trim并将空白归一为null。
     *
     * @param partId   部件ID
     * @param number   部件编号
     * @param state    生命周期状态
     * @param previous 上一代物料编码
     * @param doc      DOC规格
     * @param dpf      DPF规格
     * @param scr      SCR_ASC规格
     * @return 部件快照
     */
    public PbiFamilyPartSnapshot toSnapshot(String partId, String number, String state,
                                            String previous, String doc, String dpf, String scr) {
        PbiFamilyPartSnapshot snapshot = new PbiFamilyPartSnapshot();
        snapshot.setPartId(partId);
        snapshot.setWtPartNumber(StringUtils.trimToNull(number));
        snapshot.setState(StringUtils.trimToNull(state));
        snapshot.setPreviousMaterialCode(StringUtils.trimToNull(previous));
        snapshot.setDoc(StringUtils.trimToNull(doc));
        snapshot.setDpf(StringUtils.trimToNull(dpf));
        snapshot.setScrAsc(StringUtils.trimToNull(scr));
        return snapshot;
    }

    /**
     * 缓存快路径组装：全部字段直接取自部件Hash；父层模式下编号/状态以实时版本查询骨架为准。
     * 调用方需先保证hash非空（空Map表示Hash缺失，应跳过并告警）。
     *
     * @param partId    部件ID
     * @param hash      部件属性Hash（field大写）
     * @param fixedBase 父层固定的实时部件信息（partId -> 编号/状态）；叶层传null
     * @return 部件快照
     */
    public PbiFamilyPartSnapshot toSnapshotFromHash(String partId, Map<String, String> hash,
                                                    Map<String, PbiFamilyTreeNode> fixedBase) {
        PbiFamilyTreeNode fixedInfo = fixedBase == null ? null : fixedBase.get(partId);
        String number;
        String state;
        if (fixedInfo != null) {
            number = fixedInfo.getWtPartNumber();
            state = fixedInfo.getState();
        } else {
            number = hash.get(FIELD_NUMBER);
            state = hash.get(FIELD_STATE);
        }
        return toSnapshot(partId, number, state,
                hash.get(FIELD_PREVIOUS), hash.get(FIELD_DOC),
                hash.get(FIELD_DPF), hash.get(FIELD_SCR));
    }

    /**
     * 快照转家谱节点展示信息（不含图片/发货量/children：
     * 图片在树组装时按编号拼接，发货量由回填器补充）。
     *
     * @param snapshot 部件快照
     * @return 节点展示信息
     */
    public PbiFamilyTreeNode toNodeInfo(PbiFamilyPartSnapshot snapshot) {
        PbiFamilyTreeNode info = new PbiFamilyTreeNode();
        info.setPartId(snapshot.getPartId());
        info.setWtPartNumber(snapshot.getWtPartNumber());
        info.setState(snapshot.getState());
        info.setDoc(snapshot.getDoc());
        info.setDpf(snapshot.getDpf());
        info.setScrAsc(snapshot.getScrAsc());
        return info;
    }
}
