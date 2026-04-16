package cn.com.mfish.graph.mapper;

import cn.com.mfish.graph.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品库Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}