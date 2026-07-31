package cn.com.mfish.ai.controller;

import cn.com.mfish.ai.api.entity.McpServerConfig;
import cn.com.mfish.ai.service.McpServerConfigService;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @description: MCP服务器配置信息
 * @author: mfish
 * @date: 2026-07-21
 * @version: V2.4.1
 */
@Tag(name = "MCP服务器配置")
@RestController
@RequestMapping("/mcpConfig")
@Slf4j
public class McpServerConfigController {

    @Resource
    McpServerConfigService mcpServerConfigService;

    @Operation(summary = "MCP服务器配置-分页列表查询", description = "MCP服务器配置-分页列表查询")
    @GetMapping
    @RequiresPermissions("ai:mcpConfig:query")
    public Result<PageResult<McpServerConfig>> queryPageList(McpServerConfig req, ReqPage reqPage) {
        return mcpServerConfigService.queryPageList(req, reqPage);
    }

    @Log(title = "MCP服务器配置-新增", operateType = OperateType.INSERT)
    @Operation(summary = "MCP服务器配置-新增", description = "MCP服务器配置-新增")
    @PostMapping
    @RequiresPermissions("ai:mcpConfig:insert")
    public Result<McpServerConfig> add(@RequestBody McpServerConfig entity) {
        return mcpServerConfigService.insert(entity);
    }

    @Log(title = "MCP服务器配置-修改", operateType = OperateType.UPDATE)
    @Operation(summary = "MCP服务器配置-修改", description = "MCP服务器配置-修改")
    @PutMapping
    @RequiresPermissions("ai:mcpConfig:update")
    public Result<McpServerConfig> edit(@RequestBody McpServerConfig entity) {
        return mcpServerConfigService.update(entity);
    }

    @Log(title = "MCP服务器配置-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "MCP服务器配置-通过id删除", description = "MCP服务器配置-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("ai:mcpConfig:delete")
    public Result<Boolean> delete(@PathVariable String id) {
        return mcpServerConfigService.delete(id);
    }

    @Operation(summary = "MCP服务器配置-通过id查询", description = "MCP服务器配置-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("ai:mcpConfig:query")
    public Result<McpServerConfig> getById(@PathVariable String id) {
        return Result.ok(mcpServerConfigService.getById(id), "查询成功");
    }
}
