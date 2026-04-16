package wf.base.bean.doc;

import cn.com.mfish.common.core.entity.BaseLinkEntity;
import cn.com.mfish.common.core.entity.BaseTreeEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 文档版本迭代关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_version_link")
public class DocVersionLink extends BaseLinkEntity<String> {
}