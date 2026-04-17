package cn.com.mfish.plm.base.bean;

import cn.com.mfish.common.core.entity.BaseLinkEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部件版本迭代关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("part_version_link")
public class WFLink extends BaseLinkEntity<String> {
}