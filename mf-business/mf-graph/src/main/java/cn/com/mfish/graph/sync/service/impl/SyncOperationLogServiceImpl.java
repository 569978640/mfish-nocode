package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.sync.entity.SyncOperationLog;
import cn.com.mfish.graph.sync.req.ReqSyncOperationLog;
import cn.com.mfish.graph.sync.mapper.SyncOperationLogMapper;
import cn.com.mfish.graph.sync.service.SyncOperationLogService;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 图同步操作日志
* @author: mfish
* @date: 2026-04-18
* @version: V2.3.1
*/
@Service
public class SyncOperationLogServiceImpl extends ServiceImpl<SyncOperationLogMapper, SyncOperationLog> implements SyncOperationLogService {
    /**
     * 分页列表查询
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @return 返回图同步操作日志-分页列表
     */
    @Override
    public Result<PageResult<SyncOperationLog>> queryPageList(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqSyncOperationLog, reqPage)), "图同步操作日志-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @return 返回图同步操作日志-分页列表
     */
    private List<SyncOperationLog> queryList(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        return list();
    }

    /**
     * 添加
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-添加结果
     */
    @Override
    public Result<SyncOperationLog> add(SyncOperationLog syncOperationLog) {
        if (save(syncOperationLog)) {
            return Result.ok(syncOperationLog, "图同步操作日志-添加成功!");
        }
        return Result.fail(syncOperationLog, "错误:图同步操作日志-添加失败!");
    }

    /**
     * 编辑
     *
     * @param syncOperationLog 图同步操作日志对象
     * @return 返回图同步操作日志-编辑结果
     */
    @Override
    public Result<SyncOperationLog> edit(SyncOperationLog syncOperationLog) {
        if (updateById(syncOperationLog)) {
            return Result.ok(syncOperationLog, "图同步操作日志-编辑成功!");
        }
        return Result.fail(syncOperationLog, "错误:图同步操作日志-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志-删除结果
     */
    @Override
    public Result<Boolean> delete(Long id) {
        if (removeById(id)) {
            return Result.ok(true, "图同步操作日志-删除成功!");
        }
        return Result.fail(false, "错误:图同步操作日志-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回图同步操作日志-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "图同步操作日志-批量删除成功!");
        }
        return Result.fail(false, "错误:图同步操作日志-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回图同步操作日志对象
     */
    @Override
    public Result<SyncOperationLog> queryById(Long id) {
        SyncOperationLog syncOperationLog = getById(id);
        return Result.ok(syncOperationLog, "图同步操作日志-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqSyncOperationLog 图同步操作日志请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqSyncOperationLog reqSyncOperationLog, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("图同步操作日志_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqSyncOperationLog, reqPage));
    }
}
