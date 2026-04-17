package cn.com.mfish.plm.base.bean.doc.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.DocumentMaster;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocumentMaster;
import cn.com.mfish.plm.base.bean.doc.service.DocumentMasterService;
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
 * @description: 文档主数据
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "文档主数据")
@RestController
@RequestMapping("/documentMaster")
public class DocumentMasterController {
    @Resource
    private DocumentMasterService documentMasterService;

    /**
     * 分页列表查询
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @return 返回文档主数据-分页列表
     */
    @Operation(summary = "文档主数据-分页列表查询", description = "文档主数据-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:documentMaster:query")
    public Result<PageResult<DocumentMaster>> queryPageList(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) {
    	return documentMasterService.queryPageList(reqDocumentMaster, reqPage);
    }

    /**
     * 添加
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-添加结果
     */
    @Log(title = "文档主数据-添加", operateType = OperateType.INSERT)
    @Operation(summary = "文档主数据-添加")
    @PostMapping
    @RequiresPermissions("plm:documentMaster:insert")
    public Result<DocumentMaster> add(@RequestBody DocumentMaster documentMaster) {
    	return documentMasterService.add(documentMaster);
    }

    /**
     * 编辑
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-编辑结果
     */
    @Log(title = "文档主数据-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "文档主数据-编辑")
    @PutMapping
    @RequiresPermissions("plm:documentMaster:update")
    public Result<DocumentMaster> edit(@RequestBody DocumentMaster documentMaster) {
    	return documentMasterService.edit(documentMaster);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档主数据-删除结果
     */
    @Log(title = "文档主数据-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "文档主数据-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:documentMaster:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return documentMasterService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档主数据-删除结果
     */
    @Log(title = "文档主数据-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "文档主数据-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:documentMaster:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return documentMasterService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档主数据对象
     */
    @Operation(summary = "文档主数据-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:documentMaster:query")
    public Result<DocumentMaster> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return documentMasterService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出文档主数据", description = "导出文档主数据")
    @GetMapping("/export")
    @RequiresPermissions("plm:documentMaster:export")
    public void export(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) throws IOException {
    	documentMasterService.export(reqDocumentMaster, reqPage);
    }
}
