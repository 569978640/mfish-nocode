package cn.com.mfish.plm.base.bean.part;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFLink;

/**
 * 部件版本迭代关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("part_version_link")
public class PartVersionLink extends WFLink {
}