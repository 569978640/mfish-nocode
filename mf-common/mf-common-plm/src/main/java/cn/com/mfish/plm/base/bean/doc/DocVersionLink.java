package cn.com.mfish.plm.base.bean.doc;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.com.mfish.plm.base.bean.WFLink;

/**
 * 文档版本迭代关系实体
 *
 * @author mfish
 * @date 2026-04-16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("doc_version_link")
public class DocVersionLink extends WFLink {
}