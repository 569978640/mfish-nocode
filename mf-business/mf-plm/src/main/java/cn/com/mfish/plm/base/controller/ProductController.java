package cn.com.mfish.plm.base.controller;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.plm.base.bean.container.Product;
import cn.com.mfish.plm.base.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 产品API控制器
 * 用于测试消息流转功能
 *
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
@RestController
@RequestMapping("/product")
@Tag(name = "产品管理")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 分页列表查询
     */
    @Operation(summary = "产品-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:product:query")
    public Result<PageResult<Product>> queryPageList(Product product, ReqPage reqPage) {
        return Result.ok(new PageResult<>(productService.list()), "查询成功");
    }

    /**
     * 获取详情
     */
    @Operation(summary = "产品-获取详情")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:product:query")
    public Result<Product> queryById(@Parameter(description = "产品ID") @PathVariable String id) {
        return Result.ok(productService.getById(id), "查询成功");
    }

    /**
     * 添加产品
     */
    @Log(title = "产品-添加", operateType = OperateType.INSERT)
    @Operation(summary = "产品-添加")
    @PostMapping
    @RequiresPermissions("plm:product:insert")
    public Result<Product> add(@RequestBody Product product) {
        boolean success = productService.save(product);
        if (success) {
            return Result.ok(product, "添加成功");
        }
        return Result.fail("添加失败");
    }

    /**
     * 编辑产品
     */
    @Log(title = "产品-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "产品-编辑")
    @PutMapping
    @RequiresPermissions("plm:product:update")
    public Result<Product> edit(@RequestBody Product product) {
        boolean success = productService.updateById(product);
        if (success) {
            return Result.ok(product, "编辑成功");
        }
        return Result.fail("编辑失败");
    }

    /**
     * 删除产品
     */
    @Log(title = "产品-删除", operateType = OperateType.DELETE)
    @Operation(summary = "产品-删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:product:delete")
    public Result<String> delete(@Parameter(description = "产品ID") @PathVariable String id) {
        boolean success = productService.removeById(id);
        if (success) {
            return Result.ok("删除成功");
        }
        return Result.fail("删除失败");
    }
}