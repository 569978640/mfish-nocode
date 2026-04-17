package cn.com.mfish.plm.base.bean.folder.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.folder.entity.Folder;
import cn.com.mfish.plm.base.bean.folder.req.ReqFolder;
import cn.com.mfish.plm.base.bean.folder.mapper.FolderMapper;
import cn.com.mfish.plm.base.bean.folder.service.FolderService;
import cn.com.mfish.common.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 文件夹
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class FolderServiceImpl extends ServiceImpl<FolderMapper, Folder> implements FolderService {
    /**
     * 分页列表查询
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @return 返回文件夹-分页列表
     */
    @Override
    public Result<PageResult<Folder>> queryPageList(ReqFolder reqFolder, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqFolder, reqPage)), "文件夹-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @return 返回文件夹-分页列表
     */
    private List<Folder> queryList(ReqFolder reqFolder, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Folder> lambdaQueryWrapper = new LambdaQueryWrapper<Folder>()
                .like(!StringUtils.isEmpty(reqFolder.getName()), Folder::getName, reqFolder.getName())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-添加结果
     */
    @Override
    public Result<Folder> add(Folder folder) {
        if (save(folder)) {
            return Result.ok(folder, "文件夹-添加成功!");
        }
        return Result.fail(folder, "错误:文件夹-添加失败!");
    }

    /**
     * 编辑
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-编辑结果
     */
    @Override
    public Result<Folder> edit(Folder folder) {
        if (updateById(folder)) {
            return Result.ok(folder, "文件夹-编辑成功!");
        }
        return Result.fail(folder, "错误:文件夹-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文件夹-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "文件夹-删除成功!");
        }
        return Result.fail(false, "错误:文件夹-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文件夹-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "文件夹-批量删除成功!");
        }
        return Result.fail(false, "错误:文件夹-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文件夹对象
     */
    @Override
    public Result<Folder> queryById(String id) {
        Folder folder = getById(id);
        return Result.ok(folder, "文件夹-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqFolder reqFolder, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("文件夹_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqFolder, reqPage));
    }
}
