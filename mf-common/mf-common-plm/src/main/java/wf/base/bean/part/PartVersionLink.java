package wf.base.bean.part;

import cn.com.mfish.common.core.entity.BaseLinkEntity;
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
public class PartVersionLink extends BaseLinkEntity<String> {
}