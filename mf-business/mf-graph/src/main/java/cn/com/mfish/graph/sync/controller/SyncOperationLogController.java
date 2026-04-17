package cn.com.mfish.graph.sync.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.sync.entity.SyncOperationLog;
import cn.com.mfish.graph.sync.req.ReqSyncOperationLog;
import cn.com.mfish.graph.sync.service.SyncOperationLogService;
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
 * @description: 图同步操作日志
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "图同步操作日志")
@RestController
@RequestMapping("/syncOperationLog")
public class SyncOperationLogController {
    @Resource
    private SyncOperationLogService syncOperationLogService;

    /**
     * 分页列表查询
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @return 返回图同步操作日志-分页列表
     */
    @Operation(summary = "图同步操作日志-分页列表查询", description = "图同步操作日志-分页列表查询")
    @GetMapping
    @RequiresPermissions("graph:syncOperationLog:query")
    public Result<PageResult<SyncOperationLog>> queryPageList(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) {
    	return syncOperationLogService.queryPageList(reqSyncOperationLog, reqPage);
    }

    /**
     * 添加
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-添加结果
     */
    @Log(title = "图同步操作日志-添加", operateType = OperateType.INSERT)
    @Operation(summary = "图同步操作日志-添加")
    @PostMapping
    @RequiresPermissions("graph:syncOperationLog:insert")
    public Result<SyncOperationLog> add(@RequestBody SyncOperationLog syncOperationLog) {
    	return syncOperationLogService.add(syncOperationLog);
    }

    /**
     * 编辑
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-编辑结果
     */
    @Log(title = "图同步操作日志-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "图同步操作日志-编辑")
    @PutMapping
    @RequiresPermissions("graph:syncOperationLog:update")
    public Result<SyncOperationLog> edit(@RequestBody SyncOperationLog syncOperationLog) {
    	return syncOperationLogService.edit(syncOperationLog);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志-删除结果
     */
    @Log(title = "图同步操作日志-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "图同步操作日志-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("graph:syncOperationLog:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable Long id) {
    	return syncOperationLogService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回图同步操作日志-删除结果
     */
    @Log(title = "图同步操作日志-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "图同步操作日志-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("graph:syncOperationLog:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return syncOperationLogService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志对象
     */
    @Operation(summary = "图同步操作日志-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("graph:syncOperationLog:query")
    public Result<SyncOperationLog> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable Long id) {
    	return syncOperationLogService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出图同步操作日志", description = "导出图同步操作日志")
    @GetMapping("/export")
    @RequiresPermissions("graph:syncOperationLog:export")
    public void export(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) throws IOException {
    	syncOperationLogService.export(reqSyncOperationLog, reqPage);
    }
}
