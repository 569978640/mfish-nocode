package cn.com.mfish.plm.base.bean.folder.controller;

import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.folder.entity.Folder;
import cn.com.mfish.plm.base.bean.folder.req.ReqFolder;
import cn.com.mfish.plm.base.bean.folder.service.FolderService;
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
 * @description: 文件夹
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
@Slf4j
@Tag(name = "文件夹")
@RestController
@RequestMapping("/folder")
public class FolderController {
    @Resource
    private FolderService folderService;

    /**
     * 分页列表查询
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @return 返回文件夹-分页列表
     */
    @Operation(summary = "文件夹-分页列表查询", description = "文件夹-分页列表查询")
    @GetMapping
    @RequiresPermissions("plm:folder:query")
    public Result<PageResult<Folder>> queryPageList(ReqFolder reqFolder, ReqPage reqPage) {
    	return folderService.queryPageList(reqFolder, reqPage);
    }

    /**
     * 添加
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-添加结果
     */
    @Log(title = "文件夹-添加", operateType = OperateType.INSERT)
    @Operation(summary = "文件夹-添加")
    @PostMapping
    @RequiresPermissions("plm:folder:insert")
    public Result<Folder> add(@RequestBody Folder folder) {
    	return folderService.add(folder);
    }

    /**
     * 编辑
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-编辑结果
     */
    @Log(title = "文件夹-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "文件夹-编辑")
    @PutMapping
    @RequiresPermissions("plm:folder:update")
    public Result<Folder> edit(@RequestBody Folder folder) {
    	return folderService.edit(folder);
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文件夹-删除结果
     */
    @Log(title = "文件夹-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "文件夹-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("plm:folder:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return folderService.delete(id);
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文件夹-删除结果
     */
    @Log(title = "文件夹-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "文件夹-批量删除")
    @DeleteMapping("/batch/{ids}")
    @RequiresPermissions("plm:folder:delete")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一性ID") @PathVariable String ids) {
    	return folderService.deleteBatch(ids);
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文件夹对象
     */
    @Operation(summary = "文件夹-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("plm:folder:query")
    public Result<Folder> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
    	return folderService.queryById(id);
    }

    /**
     * 导出
	 *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出文件夹", description = "导出文件夹")
    @GetMapping("/export")
    @RequiresPermissions("plm:folder:export")
    public void export(ReqFolder reqFolder, ReqPage reqPage) throws IOException {
    	folderService.export(reqFolder, reqPage);
    }
}
