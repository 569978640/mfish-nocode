package cn.com.plm.product.report.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.plm.product.report.entity.ProductReportFieldConfig;
import cn.com.plm.product.report.req.ReqProductReportFieldConfig;
import cn.com.plm.product.report.service.ProductReportFieldConfigService;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.io.IOException;

/**
 * @description: PLM产品报表
 * @author: mfish
 * @date: 2026-04-14
 * @version: V2.3.0
 */
@Slf4j
@Tag(name = "PLM产品报表")
@RestController
@RequestMapping("/productReportFieldConfig")
public class ProductReportFieldConfigController {
    @Resource
    private ProductReportFieldConfigService productReportFieldConfigService;

    /**
     * 分页列表查询
     *
     * @param reqProductReportFieldConfig PLM产品报表请求参数
     * @param reqPage 分页参数
     * @return 返回PLM产品报表-分页列表
     */
    @Operation(summary = "PLM产品报表-分页列表查询", description = "PLM产品报表-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:productReportFieldConfig:query")
    public Result<PageResult<ProductReportFieldConfig>> queryPageList(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) {
    	return productReportFieldConfigService.queryPageList(reqProductReportFieldConfig, reqPage);
    }

    /**
     * 添加
     *
     * @param productReportFieldConfig PLM产品报表对象
     * @return 返回PLM产品报表-添加结果
     */
    @Log(title = "PLM产品报表-添加", operateType = OperateType.INSERT)
    @Operation(summary = "PLM产品报表-添加")
    @PostMapping
    @RequiresPermissions("plm:productReportFieldConfig:insert")
    public Result<ProductReportFieldConfig> add(@RequestBody ProductReportFieldConfig productReportFieldConfig) {
    	return productReportFieldConfigService.add(productReportFieldConfig);
    }

    /**
     * 编辑
     *
     * @param productReportFieldConfig PLM产品报表对象
     * @return 返回PLM产品报表-编辑结果
     */
    @Log(title = "PLM产品报表-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "PLM产品报表-编辑")
    @PutMapping
    @RequiresPermissions("plm:productReportFieldConfig:update")
    public Result<ProductReportFieldConfig> edit(@RequestBody ProductReportFieldConfig productReportFieldConfig) {
    	return productReportFieldConfigService.edit(productReportFieldConfig);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回PLM产品报表-删除结果
     */
    @Log(title = "PLM产品报表-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "PLM产品报表-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:productReportFieldConfig:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return productReportFieldConfigService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回PLM产品报表-删除结果
     */
    @Log(title = "PLM产品报表-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "PLM产品报表-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:productReportFieldConfig:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return productReportFieldConfigService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回PLM产品报表对象
     */
    @Operation(summary = "PLM产品报表-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:productReportFieldConfig:query")
    public Result<ProductReportFieldConfig> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return productReportFieldConfigService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqProductReportFieldConfig PLM产品报表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出PLM产品报表", description = "导出PLM产品报表")
    @GetMapping("/export")
    @RequiresPermissions("plm:productReportFieldConfig:export")
    public void export(ReqProductReportFieldConfig reqProductReportFieldConfig, ReqPage reqPage) throws IOException {
    	productReportFieldConfigService.export(reqProductReportFieldConfig, reqPage);
    }
}
