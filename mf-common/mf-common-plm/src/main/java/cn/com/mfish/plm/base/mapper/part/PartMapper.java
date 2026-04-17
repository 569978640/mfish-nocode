package cn.com.mfish.plm.base.mapper.part;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import cn.com.mfish.plm.base.bean.part.Part;

/**
 * 部件Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface PartMapper extends BaseMapper<Part> {
}