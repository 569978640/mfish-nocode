package cn.com.mfish.plm.base.bean.container.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.container.entity.Product;
import cn.com.mfish.plm.base.bean.container.req.ReqProduct;
import cn.com.mfish.plm.base.service.PlmBaseService;

import java.io.IOException;

/**
 * 产品库服务接口
 * 继承PlmBaseService<Product>，提供完整的CRUD操作和图同步功能
 *
 * @author mfish
 * @date 2026-04-17
 * @version: V2.3.1
 */
public interface ProductService extends PlmBaseService<Product> {

    /**
     * 分页列表查询
     *
     * @param reqProduct 产品库请求参数
     * @param reqPage 分页参数
     * @return 返回产品库-分页列表
     */
    Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage);

    /**
     * 导出
     *
     * @param reqProduct 产品库请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException;
}
