package cn.com.mfish.bfces.pbi.entity;

import lombok.Data;

/**
 * @description: 实际发货量行（接口库视图 BFCES_INTERFACE.V_WINDCHILL_SHIPMENT）。
 * ITEM 为部件编号（对应 WTPARTMASTER.WTPARTNUMBER），SHIPMENT 为实际发货量；
 * 发货量以字符串接收，规避 Oracle NUMBER 标度/小数位差异，前端可直接原样展示。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Data
public class PbiShipment {

    /** 部件编号（视图 ITEM 列，家谱按此列与 wtPartNumber 关联） */
    private String item;

    /** 实际发货量（视图 SHIPMENT 列，空白归一为 null，前端对 null 展示 0） */
    private String shipment;
}
