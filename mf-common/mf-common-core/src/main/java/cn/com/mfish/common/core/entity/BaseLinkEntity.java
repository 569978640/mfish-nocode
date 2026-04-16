package cn.com.mfish.common.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * @author: mfish
 * @description: 基础树
 * @date: 2022/11/11 17:07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "基础树对象")
public class BaseLinkEntity<T> extends BaseEntity<T> {
    @Schema(description = "起始节点ID")
    private String fromId;
    @Schema(description = "起始节点类型")
    private String fromType;
    @Schema(description = "目标节点ID")
    private String toId;
    @Schema(description = "目标节点类型")
    private String toType;
    @Schema(description = "PG库Link表的业务属性")
    private Map<String, Object> properties;
}