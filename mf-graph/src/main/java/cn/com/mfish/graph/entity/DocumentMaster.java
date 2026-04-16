package cn.com.mfish.graph.entity;

import cn.com.mfish.common.core.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档主数据实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_master")
public class DocumentMaster extends BaseEntity<String> {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String docMasterCode;
    private String docMasterName;
    private String productId;
    private String docType;
    private String status;
    private Integer sort;
    private String remark;
}