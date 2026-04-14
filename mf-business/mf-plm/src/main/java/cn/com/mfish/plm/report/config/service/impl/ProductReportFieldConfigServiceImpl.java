package cn.com.mfish.plm.report.config.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.report.config.entity.ProductReportFieldConfig;
import cn.com.mfish.plm.report.config.req.ReqProductReportFieldConfig;
import cn.com.mfish.plm.report.config.mapper.ProductReportFieldConfigMapper;
import cn.com.mfish.plm.report.config.service.ProductReportFieldConfigService;
import cn.com.mfish.common.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 产品系列报表字段配置
* @author: mfish
* @date: 2026-04-15
* @version: V2.3.0
*/
@Service
public class ProductReportFieldConfigServiceImpl extends ServiceImpl<ProductReportFieldConfigMapper, ProductReportFieldConfig> implements ProductReportFieldConfigService {
    /**
     * 分页列表查询
     *
     * @param reqProductReportFieldConfig 产品系列报表字段配置请求参数
     * @param reqPage 分页参数
     * @return 返回产品系列报表字段配置-分页列表
     */
    @Override
    public Result<PageResult<ProductReportFieldConfig>> queryPageList(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqProductReportFieldConfig, reqPage)), "产品系列报表字段配置-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqProductReportFieldConfig 产品系列报表字段配置请求参数
     * @param reqPage 分页参数
     * @return 返回产品系列报表字段配置-分页列表
     */
    private List<ProductReportFieldConfig> queryList(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<ProductReportFieldConfig> lambdaQueryWrapper = new LambdaQueryWrapper<ProductReportFieldConfig>()
                .like(!StringUtils.isEmpty(reqProductReportFieldConfig.getNumber()), ProductReportFieldConfig::getNumber, reqProductReportFieldConfig.getNumber())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param productReportFieldConfig 产品系列报表字段配置对象
     * @return 返回产品系列报表字段配置-添加结果
     */
    @Override
    public Result<ProductReportFieldConfig> add(ProductReportFieldConfig productReportFieldConfig) {
        if (save(productReportFieldConfig)) {
            return Result.ok(productReportFieldConfig, "产品系列报表字段配置-添加成功!");
        }
        return Result.fail(productReportFieldConfig, "错误:产品系列报表字段配置-添加失败!");
    }

    /**
     * 编辑
     *
     * @param productReportFieldConfig 产品系列报表字段配置对象
     * @return 返回产品系列报表字段配置-编辑结果
     */
    @Override
    public Result<ProductReportFieldConfig> edit(ProductReportFieldConfig productReportFieldConfig) {
        if (updateById(productReportFieldConfig)) {
            return Result.ok(productReportFieldConfig, "产品系列报表字段配置-编辑成功!");
        }
        return Result.fail(productReportFieldConfig, "错误:产品系列报表字段配置-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回产品系列报表字段配置-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "产品系列报表字段配置-删除成功!");
        }
        return Result.fail(false, "错误:产品系列报表字段配置-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回产品系列报表字段配置-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "产品系列报表字段配置-批量删除成功!");
        }
        return Result.fail(false, "错误:产品系列报表字段配置-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回产品系列报表字段配置对象
     */
    @Override
    public Result<ProductReportFieldConfig> queryById(String id) {
        ProductReportFieldConfig productReportFieldConfig = getById(id);
        return Result.ok(productReportFieldConfig, "产品系列报表字段配置-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqProductReportFieldConfig 产品系列报表字段配置请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("产品系列报表字段配置_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqProductReportFieldConfig, reqPage));
    }
}
