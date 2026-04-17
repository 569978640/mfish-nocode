package cn.com.mfish.graph.sync.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import cn.com.mfish.graph.sync.req.ReqSyncIdempotentLog;
import cn.com.mfish.graph.sync.mapper.SyncIdempotentLogMapper;
import cn.com.mfish.graph.sync.service.SyncIdempotentLogService;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 幂等表
* @author: mfish
* @date: 2026-04-18
* @version: V2.3.1
*/
@Service
public class SyncIdempotentLogServiceImpl extends ServiceImpl<SyncIdempotentLogMapper, SyncIdempotentLog> implements SyncIdempotentLogService {
    /**
     * 分页列表查询
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    @Override
    public Result<PageResult<SyncIdempotentLog>> queryPageList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqSyncIdempotentLog, reqPage)), "幂等表-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @return 返回幂等表-分页列表
     */
    private List<SyncIdempotentLog> queryList(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        return list();
    }

    /**
     * 添加
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-添加结果
     */
    @Override
    public Result<SyncIdempotentLog> add(SyncIdempotentLog syncIdempotentLog) {
        if (save(syncIdempotentLog)) {
            return Result.ok(syncIdempotentLog, "幂等表-添加成功!");
        }
        return Result.fail(syncIdempotentLog, "错误:幂等表-添加失败!");
    }

    /**
     * 编辑
     *
     * @param syncIdempotentLog 幂等表对象
     * @return 返回幂等表-编辑结果
     */
    @Override
    public Result<SyncIdempotentLog> edit(SyncIdempotentLog syncIdempotentLog) {
        if (updateById(syncIdempotentLog)) {
            return Result.ok(syncIdempotentLog, "幂等表-编辑成功!");
        }
        return Result.fail(syncIdempotentLog, "错误:幂等表-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回幂等表-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "幂等表-删除成功!");
        }
        return Result.fail(false, "错误:幂等表-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回幂等表-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "幂等表-批量删除成功!");
        }
        return Result.fail(false, "错误:幂等表-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回幂等表对象
     */
    @Override
    public Result<SyncIdempotentLog> queryById(String id) {
        SyncIdempotentLog syncIdempotentLog = getById(id);
        return Result.ok(syncIdempotentLog, "幂等表-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqSyncIdempotentLog 幂等表请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqSyncIdempotentLog reqSyncIdempotentLog, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("幂等表_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqSyncIdempotentLog, reqPage));
    }
}
