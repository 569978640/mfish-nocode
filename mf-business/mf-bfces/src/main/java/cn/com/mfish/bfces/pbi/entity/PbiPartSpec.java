package cn.com.mfish.bfces.pbi.entity;

import lombok.Data;

/**
 * @description: 部件规格软属性行（STRINGVALUE.VALUE2）。
 * 一次批量查询取回 DOC/DPF/SCR_ASC 三个属性定义下所有部件的值，
 * 按 partId + attrName 在Service层归并到对应家谱节点。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Data
public class PbiPartSpec {

    /** 部件ID（STRINGVALUE.IDA3A4，对应 WTPART.IDA2A2） */
    private String partId;

    /** 属性内部名（STRINGDEFINITION.NAME，如 com.bfces.DOC） */
    private String attrName;

    /** 属性值（SQL别名 SPEC_VALUE，经 MyBatis 下划线转驼峰映射到本字段，勿简写为 value） */
    private String specValue;
}
