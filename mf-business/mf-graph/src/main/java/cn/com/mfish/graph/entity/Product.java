package cn.com.mfish.graph.entity;

import cn.com.mfish.common.core.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 产品库实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity<String> {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String productCode;
    private String productName;
    private String orgId;
    private String productType;
    private String status;
    private Integer sort;
    private String remark;
}