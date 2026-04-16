package wf.base.mapper.container;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import wf.base.bean.container.Product;

/**
 * 产品库Mapper
 *
 * @author mfish
 * @date 2026-04-16
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}