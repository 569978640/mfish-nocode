package cn.com.mfish.plm.base.service.impl;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.service.PlmBaseService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * PLM基础服务实现类
 * 直接实现PlmBaseService接口（继承IService），将所有方法返回类型包装为Result
 * 提供完整的CRUD操作和图同步功能
 * 所有PLM实体服务实现类应继承此类
 *
 * @param <T> 实体类型
 * @param <M> Mapper类型
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
public abstract class PlmBaseServiceImpl<T, M extends BaseMapper<T>> extends ServiceImpl<M, T> implements PlmBaseService<T> {

    /**
     * 获取节点类型
     *
     * @return 节点类型
     */
    @Override
    public abstract String getNodeType();

    // ==================== 查询方法（返回Result包装） ====================

    @Override
    public Result<T> queryById(Serializable id) {
        T entity = super.getById(id);
        return Result.ok(entity, "查询成功!");
    }

    @Override
    public Result<List<T>> queryByIds(Collection<? extends Serializable> idList) {
        List<T> list = super.listByIds(idList);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<List<T>> queryByMap(Map<String, Object> columnMap) {
        List<T> list = super.listByMap(columnMap);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<T> queryOne(LambdaQueryWrapper<T> queryWrapper) {
        T entity = super.getOne(queryWrapper);
        return Result.ok(entity, "查询成功!");
    }

    @Override
    public Result<T> queryOne(QueryWrapper<T> queryWrapper) {
        T entity = super.getOne(queryWrapper);
        return Result.ok(entity, "查询成功!");
    }

    @Override
    public Result<List<T>> queryList(LambdaQueryWrapper<T> queryWrapper) {
        List<T> list = super.list(queryWrapper);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<List<T>> queryList(QueryWrapper<T> queryWrapper) {
        List<T> list = super.list(queryWrapper);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<List<T>> queryAll() {
        List<T> list = super.list();
        return Result.ok(list, "查询成功!");
    }

    @Override
    public Result<PageResult<T>> queryPageList(ReqPage reqPage) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        List<T> list = super.list();
        return Result.ok(new PageResult<>(list), "查询成功!");
    }

    @Override
    public Result<PageResult<T>> queryPageList(ReqPage reqPage, LambdaQueryWrapper<T> queryWrapper) {
        PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        List<T> list = super.list(queryWrapper);
        return Result.ok(new PageResult<>(list), "查询成功!");
    }

    @Override
    public Result<Long> count(LambdaQueryWrapper<T> queryWrapper) {
        long count = super.count(queryWrapper);
        return Result.ok(count, "查询成功!");
    }

    @Override
    public Result<Long> count(QueryWrapper<T> queryWrapper) {
        long count = super.count(queryWrapper);
        return Result.ok(count, "查询成功!");
    }

    @Override
    public Result<Long> countAll() {
        long count = super.count();
        return Result.ok(count, "查询成功!");
    }

    @Override
    public Result<Boolean> exists(LambdaQueryWrapper<T> queryWrapper) {
        boolean exists = super.exists(queryWrapper);
        return Result.ok(exists, "查询成功!");
    }

    // ==================== 插入方法（返回Result包装 + 图同步） ====================

    @Override
    public Result<T> insert(T entity) {
        if (super.save(entity)) {
            return Result.ok(entity, "添加成功!");
        }
        return Result.fail(entity, "添加失败!");
    }

    @Override
    public Result<Boolean> insertBatch(Collection<T> entityList) {
        if (super.saveBatch(entityList)) {
            return Result.ok(true, "批量添加成功!");
        }
        return Result.fail(false, "批量添加失败!");
    }

    @Override
    public Result<List<T>> insertBatchAndReturn(Collection<T> entityList) {
        List<T> list = new ArrayList<>(entityList);
        if (super.saveBatch(list)) {
            return Result.ok(list, "批量添加成功!");
        }
        return Result.fail(new ArrayList<>(), "批量添加失败!");
    }

    @Override
    public Result<Boolean> insertBatch(Collection<T> entityList, int batchSize) {
        List<T> list = new ArrayList<>(entityList);
        if (super.saveBatch(list, batchSize)) {
            return Result.ok(true, "批量添加成功!");
        }
        return Result.fail(false, "批量添加失败!");
    }

    @Override
    public Result<List<T>> insertBatchAndReturn(Collection<T> entityList, int batchSize) {
        List<T> list = new ArrayList<>(entityList);
        if (super.saveBatch(list, batchSize)) {
            return Result.ok(list, "批量添加成功!");
        }
        return Result.fail(new ArrayList<>(), "批量添加失败!");
    }

    // ==================== 更新方法（返回Result包装 + 图同步） ====================

    @Override
    public Result<T> updateByIdReturn(T entity) {
        if (super.updateById(entity)) {
            return Result.ok(entity, "更新成功!");
        }
        return Result.fail(entity, "更新失败!");
    }
    @Override
    public boolean updateById(T entity) {
        if (super.updateById(entity)) {
            return true;
        }
        return false;
    }

    @Override
    public Result<Boolean> update(T entity, LambdaQueryWrapper<T> updateWrapper) {
        if (super.update(entity, updateWrapper)) {
            log.info("{}条件更新成功", getNodeType());
            return Result.ok(true, "更新成功!");
        }
        return Result.fail(false, "更新失败!");
    }

    @Override
    public Result<T> updateAndReturn(T entity, LambdaQueryWrapper<T> updateWrapper) {
        if (super.update(entity, updateWrapper)) {
            return Result.ok(entity, "更新成功!");
        }
        return Result.fail(entity, "更新失败!");
    }

    @Override
    public Result<Boolean> update(T entity, QueryWrapper<T> updateWrapper) {
        if (super.update(entity, updateWrapper)) {
            return Result.ok(true, "更新成功!");
        }
        return Result.fail(false, "更新失败!");
    }

    @Override
    public Result<T> updateAndReturn(T entity, QueryWrapper<T> updateWrapper) {
        if (super.update(entity, updateWrapper)) {
            return Result.ok(entity, "更新成功!");
        }
        return Result.fail(entity, "更新失败!");
    }

    @Override
    public Result<Boolean> update(LambdaUpdateWrapper<T> updateWrapper) {
        if (super.update(updateWrapper)) {
        }
        return Result.fail(false, "更新失败!");
    }

    @Override
    public Result<Boolean> update(UpdateWrapper<T> updateWrapper) {
        if (super.update(updateWrapper)) {
            return Result.ok(true, "更新成功!");
        }
        return Result.fail(false, "更新失败!");
    }

    // ==================== 删除方法（返回Result包装 + 图同步） ====================

    @Override
    public Result<Boolean> deleteById(Serializable id) {
        if (super.removeById(id)) {
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }

    @Override
    public Result<T> deleteByIdAndReturn(Serializable id) {
        T entity = getById(id);
        if (super.removeById(id)) {
            return Result.ok(entity, "删除成功!");
        }
        return Result.fail(null, "删除失败!");
    }

    @Override
    public Result<Boolean> deleteByIds(Collection<? extends Serializable> idList) {
        if (super.removeByIds(idList)) {
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }

    @Override
    public Result<List<T>> deleteByIdsAndReturn(Collection<? extends Serializable> idList) {
        List<T> entities = listByIds(idList);
        if (super.removeByIds(idList)) {
            return Result.ok(entities, "删除成功!");
        }
        return Result.fail(new ArrayList<>(), "删除失败!");
    }

    @Override
    public Result<Boolean> deleteByMap(Map<String, Object> columnMap) {
        if (super.removeByMap(columnMap)) {
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }


    @Override
    public Result<Boolean> delete(LambdaQueryWrapper<T> queryWrapper) {
        if (super.remove(queryWrapper)) {
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }

    @Override
    public Result<Boolean> delete(QueryWrapper<T> queryWrapper) {
        if (super.remove(queryWrapper)) {
            return Result.ok(true, "删除成功!");
        }
        return Result.fail(false, "删除失败!");
    }

    // ==================== 保存或更新方法（返回Result包装 + 图同步） ====================

    @Override
    public Result<T> saveOrUpdateReturn(T entity) {
        if (super.saveOrUpdate(entity)) {
            return Result.ok(entity, "保存或更新成功!");
        }
        return Result.fail(entity, "保存或更新失败!");
    }

    @Override
    public Result<List<T>> saveOrUpdateBatchAndReturn(Collection<T> entityList) {
        List<T> list = new ArrayList<>(entityList);
        if (super.saveOrUpdateBatch(list)) {
            return Result.ok(list, "批量保存或更新成功!");
        }
        return Result.fail(new ArrayList<>(), "批量保存或更新失败!");
    }

    @Override
    public Result<List<T>> saveOrUpdateBatchAndReturn(Collection<T> entityList, int batchSize) {
        List<T> list = new ArrayList<>(entityList);
        if (super.saveOrUpdateBatch(list, batchSize)) {
            return Result.ok(list, "批量保存或更新成功!");
        }
        return Result.fail(new ArrayList<>(), "批量保存或更新失败!");
    }

    // ==================== SQL投影查询（返回Result包装） ====================

    @Override
    public <V> Result<List<V>> listObjs(LambdaQueryWrapper<T> queryWrapper) {
        List<V> list = super.listObjs(queryWrapper);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public <V> Result<List<V>> listObjs(QueryWrapper<T> queryWrapper) {
        List<V> list = super.listObjs(queryWrapper);
        return Result.ok(list, "查询成功!");
    }

    @Override
    public <V> Result<List<V>> listObjsAll() {
        List<V> list = super.listObjs();
        return Result.ok(list, "查询成功!");
    }

}
