package cn.com.mfish.plm.base.mapper.doc;

import cn.com.mfish.plm.base.bean.doc.entity.DocVersionLink;
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