package cn.com.mfish.plm.base.bean.part.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.PartMaster;
import cn.com.mfish.plm.base.bean.part.req.ReqPartMaster;
import cn.com.mfish.plm.base.bean.part.service.PartMasterService;
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
 * @description: 部件主数据
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "部件主数据")
@RestController
@RequestMapping("/partMaster")
public class PartMasterController {
    @Resource
    private PartMasterService partMasterService;

    /**
     * 分页列表查询
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @return 返回部件主数据-分页列表
     */
    @Operation(summary = "部件主数据-分页列表查询", description = "部件主数据-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:partMaster:query")
    public Result<PageResult<PartMaster>> queryPageList(ReqPartMaster reqPartMaster, ReqPage reqPage) {
    	return partMasterService.queryPageList(reqPartMaster, reqPage);
    }

    /**
     * 添加
     *
     * @param partMaster 部件主数据对象
     * @return 返回部件主数据-添加结果
     */
    @Log(title = "部件主数据-添加", operateType = OperateType.INSERT)
    @Operation(summary = "部件主数据-添加")
    @PostMapping
    @RequiresPermissions("plm:partMaster:insert")
    public Result<PartMaster> add(@RequestBody PartMaster partMaster) {
    	return partMasterService.add(partMaster);
    }

    /**
     * 编辑
     *
     * @param partMaster 部件主数据对象
     * @return 返回部件主数据-编辑结果
     */
    @Log(title = "部件主数据-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "部件主数据-编辑")
    @PutMapping
    @RequiresPermissions("plm:partMaster:update")
    public Result<PartMaster> edit(@RequestBody PartMaster partMaster) {
    	return partMasterService.edit(partMaster);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件主数据-删除结果
     */
    @Log(title = "部件主数据-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "部件主数据-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:partMaster:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return partMasterService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件主数据-删除结果
     */
    @Log(title = "部件主数据-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "部件主数据-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:partMaster:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return partMasterService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件主数据对象
     */
    @Operation(summary = "部件主数据-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:partMaster:query")
    public Result<PartMaster> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return partMasterService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出部件主数据", description = "导出部件主数据")
    @GetMapping("/export")
    @RequiresPermissions("plm:partMaster:export")
    public void export(ReqPartMaster reqPartMaster, ReqPage reqPage) throws IOException {
    	partMasterService.export(reqPartMaster, reqPage);
    }
}
