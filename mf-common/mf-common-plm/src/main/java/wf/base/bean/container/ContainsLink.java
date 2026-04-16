package wf.base.bean.container;

import cn.com.mfish.common.core.entity.BaseLinkEntity;
import cn.com.mfish.common.core.entity.BaseTreeEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import wf.base.bean.WFLink;
import wf.base.bean.WFObject;

import java.util.Map;

/**
 * 包含关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contains_link")
public class ContainsLink extends WFLink {

}