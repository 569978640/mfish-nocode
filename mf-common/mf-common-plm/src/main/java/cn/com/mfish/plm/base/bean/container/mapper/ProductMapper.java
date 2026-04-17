package cn.com.mfish.plm.base.bean.container.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import cn.com.mfish.plm.base.bean.container.entity.Product;

/**
 * 产品库Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}