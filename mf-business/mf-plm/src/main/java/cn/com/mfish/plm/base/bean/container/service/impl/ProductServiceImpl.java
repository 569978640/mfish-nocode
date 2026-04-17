package cn.com.mfish.plm.base.bean.container.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.container.entity.Product;
import cn.com.mfish.plm.base.bean.container.mapper.ProductMapper;
import cn.com.mfish.plm.base.bean.container.req.ReqProduct;
import cn.com.mfish.plm.base.bean.container.service.ProductService;
import cn.com.mfish.plm.base.service.impl.PlmBaseServiceImpl;
import cn.com.mfish.common.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 产品库服务实现
 * 继承PlmBaseServiceImpl，提供完整的CRUD操作和图同步功能
 * 仅需实现Product特定的方法和getNodeType()
 *
 * @author mfish
 * @date 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Service
public class ProductServiceImpl extends PlmBaseServiceImpl<Product, ProductMapper> implements ProductService {

    @Override
    public String getNodeType() {
        return "Product";
    }

    @Override
    public Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(!StringUtils.isEmpty(reqProduct.getId()), Product::getId, reqProduct.getId())
                .like(!StringUtils.isEmpty(reqProduct.getName()), Product::getName, reqProduct.getName())
                .eq(!StringUtils.isEmpty(reqProduct.getCreate_by()), Product::getCreateBy, reqProduct.getCreate_by());
        List<Product> list = list(wrapper);
        return Result.ok(new PageResult<>(list), "产品库-查询成功!");
    }

    @Override
    public void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(!StringUtils.isEmpty(reqProduct.getId()), Product::getId, reqProduct.getId())
                .like(!StringUtils.isEmpty(reqProduct.getName()), Product::getName, reqProduct.getName());
        List<Product> list = list(wrapper);
        ExcelUtils.write("产品库_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), list);
    }
}
