package cn.com.mfish.plm.base.bean.link.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.ContainsLink;
import cn.com.mfish.plm.base.bean.link.req.ReqContainsLink;
import cn.com.mfish.plm.base.bean.link.service.ContainsLinkService;
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
 * @description: 包含关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "包含关系")
@RestController
@RequestMapping("/containsLink")
public class ContainsLinkController {
    @Resource
    private ContainsLinkService containsLinkService;

    /**
     * 分页列表查询
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @return 返回包含关系-分页列表
     */
    @Operation(summary = "包含关系-分页列表查询", description = "包含关系-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:containsLink:query")
    public Result<PageResult<ContainsLink>> queryPageList(ReqContainsLink reqContainsLink, ReqPage reqPage) {
    	return containsLinkService.queryPageList(reqContainsLink, reqPage);
    }

    /**
     * 添加
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-添加结果
     */
    @Log(title = "包含关系-添加", operateType = OperateType.INSERT)
    @Operation(summary = "包含关系-添加")
    @PostMapping
    @RequiresPermissions("plm:containsLink:insert")
    public Result<ContainsLink> add(@RequestBody ContainsLink containsLink) {
    	return containsLinkService.add(containsLink);
    }

    /**
     * 编辑
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-编辑结果
     */
    @Log(title = "包含关系-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "包含关系-编辑")
    @PutMapping
    @RequiresPermissions("plm:containsLink:update")
    public Result<ContainsLink> edit(@RequestBody ContainsLink containsLink) {
    	return containsLinkService.edit(containsLink);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回包含关系-删除结果
     */
    @Log(title = "包含关系-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "包含关系-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:containsLink:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return containsLinkService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回包含关系-删除结果
     */
    @Log(title = "包含关系-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "包含关系-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:containsLink:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return containsLinkService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回包含关系对象
     */
    @Operation(summary = "包含关系-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:containsLink:query")
    public Result<ContainsLink> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return containsLinkService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出包含关系", description = "导出包含关系")
    @GetMapping("/export")
    @RequiresPermissions("plm:containsLink:export")
    public void export(ReqContainsLink reqContainsLink, ReqPage reqPage) throws IOException {
    	containsLinkService.export(reqContainsLink, reqPage);
    }
}
