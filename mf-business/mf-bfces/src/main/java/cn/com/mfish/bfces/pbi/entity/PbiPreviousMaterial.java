package cn.com.mfish.bfces.pbi.entity;

import lombok.Data;

/**
 * @description: 上一代物料编码（STRINGVALUE行）映射对象。
 * IDA3A4 为当前部件ID，VALUE 为上一代部件编号；
 * VALUE 为 BASELINE 表示当前部件即家谱顶层，VALUE 为空或查不到行表示该支系不进入家谱。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Data
public class PbiPreviousMaterial {

    /** 当前部件ID（STRINGVALUE.IDA3A4） */
    private String partId;

    /** 上一代物料部件编号（STRINGVALUE.VALUE，特殊值 BASELINE 表示顶层） */
    private String previousValue;
}
