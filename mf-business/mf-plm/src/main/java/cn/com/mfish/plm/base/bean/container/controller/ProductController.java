package cn.com.mfish.plm.base.bean.container.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.container.entity.Product;
import cn.com.mfish.plm.base.bean.container.req.ReqProduct;
import cn.com.mfish.plm.base.bean.container.service.ProductService;
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
 * @description: 产品库
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "产品库")
@RestController
@RequestMapping("/product")
public class ProductController {
    @Resource
    private ProductService productService;

    /**
     * 分页列表查询
     *
     * @param reqProduct 产品库请求参数
     * @param reqPage    分页参数
     * @return 返回产品库-分页列表
     */
    @Operation(summary = "产品库-分页列表查询", description = "产品库-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:product:query")
    public Result<PageResult<Product>> queryPageList(ReqProduct reqProduct, ReqPage reqPage) {
        return productService.queryPageList(reqProduct, reqPage);
    }

    /**
     * 添加
     *
     * @param product 产品库对象
     * @return 返回产品库-添加结果
     */
    @Log(title = "产品库-添加", operateType = OperateType.INSERT)
    @Operation(summary = "产品库-添加")
    @PostMapping
    @RequiresPermissions("plm:product:insert")
    public Result<Product> add(@RequestBody Product product) {
        return productService.add(product);
    }

    /**
     * 编辑
     *
     * @param product 产品库对象
     * @return 返回产品库-编辑结果
     */
    @Log(title = "产品库-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "产品库-编辑")
    @PutMapping
    @RequiresPermissions("plm:product:update")
    public Result<Product> edit(@RequestBody Product product) {
        return productService.update(product);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回产品库-删除结果
     */
    @Log(title = "产品库-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "产品库-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:product:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
        return productService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回产品库-删除结果
     */
    @Log(title = "产品库-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "产品库-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:product:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
        return productService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回产品库对象
     */
    @Operation(summary = "产品库-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:product:query")
    public Result<Product> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
        return productService.queryById(id);
    }

    /**
     * 导出
     *
     * @param reqProduct 产品库请求参数
     * @param reqPage    分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出产品库", description = "导出产品库")
    @GetMapping("/export")
    @RequiresPermissions("plm:product:export")
    public void export(ReqProduct reqProduct, ReqPage reqPage) throws IOException {
        productService.export(reqProduct, reqPage);
    }
}
