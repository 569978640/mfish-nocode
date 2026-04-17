package cn.com.mfish.plm.base.bean.link.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.IteraiteLink;
import cn.com.mfish.plm.base.bean.link.req.ReqIteraiteLink;
import cn.com.mfish.plm.base.bean.link.service.IteraiteLinkService;
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
 * @description: 迭代关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "迭代关系")
@RestController
@RequestMapping("/iteraiteLink")
public class IteraiteLinkController {
    @Resource
    private IteraiteLinkService iteraiteLinkService;

    /**
     * 分页列表查询
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @return 返回迭代关系-分页列表
     */
    @Operation(summary = "迭代关系-分页列表查询", description = "迭代关系-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:iteraiteLink:query")
    public Result<PageResult<IteraiteLink>> queryPageList(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) {
    	return iteraiteLinkService.queryPageList(reqIteraiteLink, reqPage);
    }

    /**
     * 添加
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-添加结果
     */
    @Log(title = "迭代关系-添加", operateType = OperateType.INSERT)
    @Operation(summary = "迭代关系-添加")
    @PostMapping
    @RequiresPermissions("plm:iteraiteLink:insert")
    public Result<IteraiteLink> add(@RequestBody IteraiteLink iteraiteLink) {
    	return iteraiteLinkService.add(iteraiteLink);
    }

    /**
     * 编辑
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-编辑结果
     */
    @Log(title = "迭代关系-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "迭代关系-编辑")
    @PutMapping
    @RequiresPermissions("plm:iteraiteLink:update")
    public Result<IteraiteLink> edit(@RequestBody IteraiteLink iteraiteLink) {
    	return iteraiteLinkService.edit(iteraiteLink);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回迭代关系-删除结果
     */
    @Log(title = "迭代关系-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "迭代关系-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:iteraiteLink:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return iteraiteLinkService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回迭代关系-删除结果
     */
    @Log(title = "迭代关系-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "迭代关系-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:iteraiteLink:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return iteraiteLinkService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回迭代关系对象
     */
    @Operation(summary = "迭代关系-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:iteraiteLink:query")
    public Result<IteraiteLink> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return iteraiteLinkService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出迭代关系", description = "导出迭代关系")
    @GetMapping("/export")
    @RequiresPermissions("plm:iteraiteLink:export")
    public void export(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) throws IOException {
    	iteraiteLinkService.export(reqIteraiteLink, reqPage);
    }
}
