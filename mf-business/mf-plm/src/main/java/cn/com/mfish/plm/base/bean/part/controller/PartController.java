package cn.com.mfish.plm.base.bean.part.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.WPart;
import cn.com.mfish.plm.base.bean.part.req.ReqPart;
import cn.com.mfish.plm.base.bean.part.service.PartService;
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
 * @description: 部件小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "部件小版本")
@RestController
@RequestMapping("/part")
public class PartController {
    @Resource
    private PartService partService;

    /**
     * 分页列表查询
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @return 返回部件小版本-分页列表
     */
    @Operation(summary = "部件小版本-分页列表查询", description = "部件小版本-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:part:query")
    public Result<PageResult<WPart>> queryPageList(ReqPart reqPart, ReqPage reqPage) {
    	return partService.queryPageList(reqPart, reqPage);
    }

    /**
     * 添加
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-添加结果
     */
    @Log(title = "部件小版本-添加", operateType = OperateType.INSERT)
    @Operation(summary = "部件小版本-添加")
    @PostMapping
    @RequiresPermissions("plm:wPart:insert")
    public Result<WPart> add(@RequestBody WPart wPart) {
    	return partService.add(wPart);
    }

    /**
     * 编辑
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-编辑结果
     */
    @Log(title = "部件小版本-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "部件小版本-编辑")
    @PutMapping
    @RequiresPermissions("plm:wPart:update")
    public Result<WPart> edit(@RequestBody WPart wPart) {
    	return partService.edit(wPart);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件小版本-删除结果
     */
    @Log(title = "部件小版本-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "部件小版本-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:part:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return partService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件小版本-删除结果
     */
    @Log(title = "部件小版本-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "部件小版本-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:part:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return partService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件小版本对象
     */
    @Operation(summary = "部件小版本-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:part:query")
    public Result<WPart> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return partService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出部件小版本", description = "导出部件小版本")
    @GetMapping("/export")
    @RequiresPermissions("plm:part:export")
    public void export(ReqPart reqPart, ReqPage reqPage) throws IOException {
    	partService.export(reqPart, reqPage);
    }
}
