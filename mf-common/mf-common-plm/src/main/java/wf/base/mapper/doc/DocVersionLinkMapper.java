package wf.base.mapper.doc;

import wf.base.bean.doc.DocVersionLink;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档版本迭代关系Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface DocVersionLinkMapper extends BaseMapper<DocVersionLink> {
}