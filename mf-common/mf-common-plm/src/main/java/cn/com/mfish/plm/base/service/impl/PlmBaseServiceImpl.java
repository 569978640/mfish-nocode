package cn.com.mfish.plm.base.service.impl;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.service.PlmBaseService;
import cn.com.mfish.plm.base.service.PlmGraphSyncClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

/**
 * PLM基础服务实现类
 * 提供完整的CRUD操作和图同步功能
 * 所有PLM实体服务实现类应继承此类
 *
 * @param <T> 实体类型
 * @param <M> Mapper类型
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
public abstract class PlmBaseServiceImpl<T, M> extends ServiceImpl<M, T> implements PlmBaseService<T> {

    @Autowired
    protected PlmGraphSyncClient plmGraphSyncClient;

    @Override
    public Result<PageResult<T>> queryPageList(ReqPage reqPage) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        List<T> list = list();
        return Result.ok(new PageResult<>(list), "查询成功!");
    }

    @Override
    public Result<T> save(T entity) {
        if (super.save(entity)) {
            plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "CREATE");
            log.info("{}添加成功并发送图同步事件", getNodeType());
            return Result.ok(entity, "添加成功!");
        }
        return Result.fail(entity, "添加失败!");
    }

    @Override
    public Result<Boolean> addBatch(List<T> list) {
        if (saveBatch(list)) {
            plmGraphSyncClient.sendGraphSyncEventBatch(list, getNodeType(), "CREATE");
            log.info("{}批量添加成功并发送图同步事件: count={}", getNodeType(), list.size());
            return Result.ok(true, "批量添加成功!");
        }
        return Result.fail(false, "批量添加失败!");
    }

    @Override
    public Result<T> update(T entity) {
        if (updateById(entity)) {
            plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "UPDATE");
            log.info("{}更新成功并发送图同步事件", getNodeType());
            return Result.ok(entity, "更新成功!");
        }
        return Result.fail(entity, "更新失败!");
    }

    @Override
    public Result<Boolean> delete(Serializable id) {
        T entity = getById(id);
        if (removeById(id)) {
            if (entity != null) {
                plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "DELETE");
                log.info("{}删除成功并发送图同步事件: id={}", getNodeType(), id);
            }
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }

    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail(false, "ID列表不能为空!");
        }
        List<T> entities = listByIds(Arrays.asList(ids.split(",")));
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            if (entities != null && !entities.isEmpty()) {
                plmGraphSyncClient.sendGraphSyncEventBatch(entities, getNodeType(), "DELETE");
                log.info("{}批量删除成功并发送图同步事件: count={}", getNodeType(), entities.size());
            }
            return Result.ok(true, "批量删除成功!");
        }
        return Result.fail(false, "批量删除失败!");
    }

    @Override
    public Result<T> queryById(Serializable id) {
        T entity = getById(id);
        return Result.ok(entity, "查询成功!");
    }

    @Override
    public Result<List<T>> queryByIds(List<Serializable> ids) {
        List<T> list = listByIds(ids);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<List<T>> queryList(LambdaQueryWrapper<T> wrapper) {
        List<T> list = list(wrapper);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<T> saveOrUpdateEntity(T entity) {
        if (saveOrUpdate(entity)) {
            plmGraphSyncClient.sendGraphSyncEvent(entity, getNodeType(), "CREATE");
            log.info("{}保存或更新成功并发送图同步事件", getNodeType());
            return Result.ok(entity, "保存或更新成功!");
        }
        return Result.fail(entity, "保存或更新失败!");
    }

    @Override
    public Result<Boolean> saveOrUpdateBatch(List<T> list) {
        if (saveOrUpdateBatch(list)) {
            plmGraphSyncClient.sendGraphSyncEventBatch(list, getNodeType(), "CREATE");
            log.info("{}批量保存或更新成功并发送图同步事件: count={}", getNodeType(), list.size());
            return Result.ok(true, "批量保存或更新成功!");
        }
        return Result.fail(false, "批量保存或更新失败!");
    }
}
