package cn.com.mfish.graph.sync.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.req.ReqSyncIdempotentLog;
import cn.com.mfish.graph.sync.service.SyncIdempotentLogService;
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
 * @description: 幂等表
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "幂等表")
@RestController
@RequestMapping("/syncIdempotentLog")
public class SyncIdempotentLogController {
    @Resource
    private SyncIdempotentLogService syncIdempotentLogService;

    /**
     * 分页列表查询
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    @Operation(summary = "幂等表-分页列表查询", description = "幂等表-分页列表查询")
    @GetMapping
    @RequiresPermissions("graph:syncIdempotentLog:query")
    public Result<PageResult<SyncIdempotentLog>> queryPageList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) {
    	return syncIdempotentLogService.queryPageList(reqSyncIdempotentLog, reqPage);
    }

    /**
     * 添加
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-添加结果
     */
    @Log(title = "幂等表-添加", operateType = OperateType.INSERT)
    @Operation(summary = "幂等表-添加")
    @PostMapping
    @RequiresPermissions("graph:syncIdempotentLog:insert")
    public Result<SyncIdempotentLog> add(@RequestBody SyncIdempotentLog syncIdempotentLog) {
    	return syncIdempotentLogService.add(syncIdempotentLog);
    }

    /**
     * 编辑
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-编辑结果
     */
    @Log(title = "幂等表-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "幂等表-编辑")
    @PutMapping
    @RequiresPermissions("graph:syncIdempotentLog:update")
    public Result<SyncIdempotentLog> edit(@RequestBody SyncIdempotentLog syncIdempotentLog) {
    	return syncIdempotentLogService.edit(syncIdempotentLog);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回幂等表-删除结果
     */
    @Log(title = "幂等表-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "幂等表-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("graph:syncIdempotentLog:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return syncIdempotentLogService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回幂等表-删除结果
     */
    @Log(title = "幂等表-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "幂等表-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("graph:syncIdempotentLog:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return syncIdempotentLogService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回幂等表对象
     */
    @Operation(summary = "幂等表-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("graph:syncIdempotentLog:query")
    public Result<SyncIdempotentLog> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return syncIdempotentLogService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出幂等表", description = "导出幂等表")
    @GetMapping("/export")
    @RequiresPermissions("graph:syncIdempotentLog:export")
    public void export(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) throws IOException {
    	syncIdempotentLogService.export(reqSyncIdempotentLog, reqPage);
    }
}
