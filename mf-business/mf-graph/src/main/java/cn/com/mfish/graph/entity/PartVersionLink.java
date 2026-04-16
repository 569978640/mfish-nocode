package cn.com.mfish.graph.entity;

import cn.com.mfish.common.core.entity.BaseTreeEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 部件版本迭代关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("part_version_link")
public class PartVersionLink extends BaseTreeEntity<String> {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String type;
    private String fromId;
    private String fromType;
    private String toId;
    private String toType;
    private Map<String, Object> properties;
}