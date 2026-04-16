package wf.base.mapper.doc;

import wf.base.bean.doc.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}