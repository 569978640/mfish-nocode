package cn.com.plm.product.report.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.plm.product.report.entity.ProductReportFieldConfig;
import cn.com.plm.product.report.req.ReqProductReportFieldConfig;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: PLM产品报表
 * @author: mfish
 * @date: 2026-04-14
 * @version: V2.3.0
 */
public interface ProductReportFieldConfigService extends IService<ProductReportFieldConfig> {
    /**
     * 分页列表查询
     *
     * @param reqProductReportFieldConfig PLM产品报表请求参数
     * @param reqPage 分页参数
     * @return 返回PLM产品报表-分页列表
     */
    Result<PageResult<ProductReportFieldConfig>> queryPageList(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage);

    /**
     * 添加
     *
     * @param productReportFieldConfig PLM产品报表对象
     * @return 返回PLM产品报表-添加结果
     */
    Result<ProductReportFieldConfig> add(ProductReportFieldConfig productReportFieldConfig);

    /**
     * 编辑
     *
     * @param productReportFieldConfig PLM产品报表对象
     * @return 返回PLM产品报表-编辑结果
     */
    Result<ProductReportFieldConfig> edit(ProductReportFieldConfig productReportFieldConfig);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回PLM产品报表-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回PLM产品报表-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回PLM产品报表对象
     */
    Result<ProductReportFieldConfig> queryById(String id);

    /**
     * 导出
     *
     * @param reqProductReportFieldConfig PLM产品报表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) throws IOException;
}
