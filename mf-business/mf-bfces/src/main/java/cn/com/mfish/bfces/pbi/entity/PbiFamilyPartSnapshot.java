package cn.com.mfish.bfces.pbi.entity;

import lombok.Data;

/**
 * @description: PBI产品家谱部件快照（家谱递归过程中的部件只读数据）。
 * 数据来源优先取Redis部件Hash（pbi:part:detail:{IDA2A2}，列来自BFCES_PBI_PART_VIEW），
 * Redis不可用时由PbiFamilyMapper的WTPART/STRINGVALUE关联查询降级组装。
 * previousMaterialCode为"上一代物料编码"（父部件编号或BASELINE），空白表示该支系不进入家谱；
 * doc/dpf/scrAsc为规格属性，空白归一为null，前端按null隐藏对应标签。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
@Data
public class PbiFamilyPartSnapshot {

    /** 部件ID（WTPART.IDA2A2） */
    private String partId;

    /** 部件编号 */
    private String wtPartNumber;

    /** 生命周期状态（视图STATE列，如 RELEASED/OBSOLETE；无值为null） */
    private String state;

    /** 上一代物料编码（父部件编号；BASELINE表示家谱顶层；空白表示支系剪枝） */
    private String previousMaterialCode;

    /** DOC规格（无值为null） */
    private String doc;

    /** DPF规格（无值为null） */
    private String dpf;

    /** SCR规格（取SCR_ASC定义，无值为null） */
    private String scrAsc;
}
