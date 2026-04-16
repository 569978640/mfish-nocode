package wf.base.bean;

import cn.com.mfish.common.core.entity.BaseEntity;
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
public class WFObject extends BaseEntity<String> {
    private String type;
}