package cn.com.mfish.plm.base.bean.doc.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.Document;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocument;
import cn.com.mfish.plm.base.bean.doc.service.DocumentService;
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
 * @description: 文档小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "文档小版本")
@RestController
@RequestMapping("/document")
public class DocumentController {
    @Resource
    private DocumentService documentService;

    /**
     * 分页列表查询
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @return 返回文档小版本-分页列表
     */
    @Operation(summary = "文档小版本-分页列表查询", description = "文档小版本-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:document:query")
    public Result<PageResult<Document>> queryPageList(ReqDocument reqDocument, ReqPage reqPage) {
    	return documentService.queryPageList(reqDocument, reqPage);
    }

    /**
     * 添加
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-添加结果
     */
    @Log(title = "文档小版本-添加", operateType = OperateType.INSERT)
    @Operation(summary = "文档小版本-添加")
    @PostMapping
    @RequiresPermissions("plm:document:insert")
    public Result<Document> add(@RequestBody Document document) {
    	return documentService.add(document);
    }

    /**
     * 编辑
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-编辑结果
     */
    @Log(title = "文档小版本-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "文档小版本-编辑")
    @PutMapping
    @RequiresPermissions("plm:document:update")
    public Result<Document> edit(@RequestBody Document document) {
    	return documentService.edit(document);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档小版本-删除结果
     */
    @Log(title = "文档小版本-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "文档小版本-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:document:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return documentService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档小版本-删除结果
     */
    @Log(title = "文档小版本-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "文档小版本-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:document:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return documentService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档小版本对象
     */
    @Operation(summary = "文档小版本-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:document:query")
    public Result<Document> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return documentService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出文档小版本", description = "导出文档小版本")
    @GetMapping("/export")
    @RequiresPermissions("plm:document:export")
    public void export(ReqDocument reqDocument, ReqPage reqPage) throws IOException {
    	documentService.export(reqDocument, reqPage);
    }
}
