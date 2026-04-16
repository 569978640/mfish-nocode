package wf.base.mapper.part;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import wf.base.bean.part.Part;

/**
 * 部件Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface PartMapper extends BaseMapper<Part> {
}