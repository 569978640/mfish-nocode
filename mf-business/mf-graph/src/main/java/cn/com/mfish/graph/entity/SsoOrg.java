package cn.com.mfish.graph.entity;

import cn.com.mfish.common.core.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sso_org")
public class SsoOrg extends BaseEntity<String> {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String orgCode;
    private String orgName;
    private String parentId;
    private Integer orgLevel;
    private String orgPath;
    private Integer sort;
    private Integer status;
    private String orgType;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private String remark;
}