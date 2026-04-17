package cn.com.mfish.plm.base.bean.container.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.container.entity.Product;
import cn.com.mfish.plm.base.bean.container.req.ReqProduct;
import cn.com.mfish.plm.base.service.PlmBaseService;

import java.io.IOException;
import java.io.Serializable;

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
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回产品库对象
     */
    @Override
    Result<Product> queryById(Serializable id);

    /**
     * 导出
     *
     * @param reqProduct 产品库请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException;


    /**
     * 添加
     *
     * @param product 产品库对象
     * @return 返回产品库-添加结果
     */
    Result<Product> add(Product product);

    /**
     * 编辑
     *
     * @param product 产品库对象
     * @return 返回产品库-编辑结果
     */
    Result<Product> update(Product product);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回产品库-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回产品库-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回产品库对象
     */
    Result<Product> queryById(String id);
}
