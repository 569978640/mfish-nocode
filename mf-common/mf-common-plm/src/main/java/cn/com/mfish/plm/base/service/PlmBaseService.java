package cn.com.mfish.plm.base.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * PLM基础服务接口
 * 直接实现MyBatis-Plus IService，将所有方法返回类型包装为Result
 * 提供完整的CRUD操作和图同步功能
 * 所有PLM实体服务接口应继承此接口
 *
 * @param <T> 实体类型
 * @author mfish
 * @date 2026-04-17
 */
public interface PlmBaseService<T> extends IService<T> {

    /**
     * 获取节点类型
     *
     * @return 节点类型
     */
    String getNodeType();

    // ==================== 查询方法（返回Result包装） ====================

    /**
     * 通过ID查询
     *
     * @param id 主键ID
     * @return 查询结果
     */
    Result<T> queryById(Serializable id);

    /**
     * 根据ID列表查询
     *
     * @param idList ID列表
     * @return 实体列表
     */
    Result<List<T>> queryByIds(Collection<? extends Serializable> idList);

    /**
     * 根据ColumnMap查询
     *
     * @param columnMap 列名-值映射
     * @return 实体列表
     */
    Result<List<T>> queryByMap(Map<String, Object> columnMap);

    /**
     * 根据条件查询单条记录
     *
     * @param queryWrapper 查询条件
     * @return 实体
     */
    Result<T> queryOne(LambdaQueryWrapper<T> queryWrapper);

    /**
     * 根据条件查询单条记录
     *
     * @param queryWrapper 查询条件
     * @return 实体
     */
    Result<T> queryOne(QueryWrapper<T> queryWrapper);

    /**
     * 根据条件查询列表
     *
     * @param queryWrapper 查询条件
     * @return 实体列表
     */
    Result<List<T>> queryList(LambdaQueryWrapper<T> queryWrapper);

    /**
     * 根据条件查询列表
     *
     * @param queryWrapper 查询条件
     * @return 实体列表
     */
    Result<List<T>> queryList(QueryWrapper<T> queryWrapper);

    /**
     * 查询所有记录
     *
     * @return 实体列表
     */
    Result<List<T>> queryAll();

    /**
     * 分页查询
     *
     * @param reqPage 分页参数
     * @return 分页结果
     */
    Result<PageResult<T>> queryPageList(ReqPage reqPage);

    /**
     * 分页查询（带条件）
     *
     * @param reqPage 分页参数
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    Result<PageResult<T>> queryPageList(ReqPage reqPage, LambdaQueryWrapper<T> queryWrapper);

    /**
     * 统计记录数
     *
     * @param queryWrapper 查询条件
     * @return 记录数
     */
    Result<Long> count(LambdaQueryWrapper<T> queryWrapper);

    /**
     * 统计记录数
     *
     * @param queryWrapper 查询条件
     * @return 记录数
     */
    Result<Long> count(QueryWrapper<T> queryWrapper);

    /**
     * 统计所有记录数
     *
     * @return 记录数
     */
    Result<Long> countAll();

    /**
     * 判断记录是否存在
     *
     * @param queryWrapper 查询条件
     * @return 是否存在
     */
    Result<Boolean> exists(LambdaQueryWrapper<T> queryWrapper);

    // ==================== 插入方法（返回Result包装 + 图同步） ====================

    /**
     * 插入记录（单条）
     *
     * @param entity 实体
     * @return 操作结果
     */
    Result<T> insert(T entity);

    /**
     * 批量插入
     *
     * @param entityList 实体列表
     * @return 操作结果
     */
    Result<Boolean> insertBatch(Collection<T> entityList);

    /**
     * 批量插入并返回成功插入的实体列表
     *
     * @param entityList 实体列表
     * @return 成功插入的实体列表
     */
    Result<List<T>> insertBatchAndReturn(Collection<T> entityList);

    /**
     * 批量插入（分批次）
     *
     * @param entityList 实体列表
     * @param batchSize 每批数量
     * @return 操作结果
     */
    Result<Boolean> insertBatch(Collection<T> entityList, int batchSize);

    /**
     * 批量插入（分批次）并返回成功插入的实体列表
     *
     * @param entityList 实体列表
     * @param batchSize 每批数量
     * @return 成功插入的实体列表
     */
    Result<List<T>> insertBatchAndReturn(Collection<T> entityList, int batchSize);

    // ==================== 更新方法（返回Result包装 + 图同步） ====================

    /**
     * 根据ID更新
     *
     * @param entity 实体
     * @return 操作结果
     */
    Result<T> updateByIdReturn(T entity);
    /**
     * 根据ID更新
     *
     * @param entity 实体
     * @return 操作结果
     */
    boolean updateById(T entity);

    /**
     * 根据条件更新
     *
     * @param entity 实体
     * @param updateWrapper 更新条件
     * @return 操作结果
     */
    Result<Boolean> update(T entity, LambdaQueryWrapper<T> updateWrapper);


    /**
     * 根据条件更新
     *
     * @param entity 实体
     * @param updateWrapper 更新条件
     * @return 操作结果
     */
    Result<Boolean> update(T entity, QueryWrapper<T> updateWrapper);


    /**
     * 根据条件更新
     *
     * @param updateWrapper 更新条件
     * @return 操作结果
     */
    Result<Boolean> update(LambdaUpdateWrapper<T> updateWrapper);


    /**
     * 根据条件更新
     *
     * @param updateWrapper 更新条件
     * @return 操作结果
     */
    Result<Boolean> update(UpdateWrapper<T> updateWrapper);


    // ==================== 删除方法（返回Result包装 + 图同步） ====================

    /**
     * 根据ID删除
     *
     * @param id 主键ID
     * @return 操作结果
     */
    Result<Boolean> deleteById(Serializable id);

    /**
     * 根据ID删除并返回被删除的实体
     *
     * @param id 主键ID
     * @return 被删除的实体
     */
    Result<T> deleteByIdAndReturn(Serializable id);

    /**
     * 根据ID列表删除
     *
     * @param idList ID列表
     * @return 操作结果
     */
    Result<Boolean> deleteByIds(Collection<? extends Serializable> idList);

    /**
     * 根据ID列表删除并返回被删除的实体列表
     *
     * @param idList ID列表
     * @return 被删除的实体列表
     */
    Result<List<T>> deleteByIdsAndReturn(Collection<? extends Serializable> idList);

    /**
     * 根据ColumnMap删除
     *
     * @param columnMap 列名-值映射
     * @return 操作结果
     */
    Result<Boolean> deleteByMap(Map<String, Object> columnMap);

    /**
     * 根据条件删除
     *
     * @param queryWrapper 查询条件
     * @return 操作结果
     */
    Result<Boolean> delete(LambdaQueryWrapper<T> queryWrapper);

    /**
     * 根据条件删除
     *
     * @param queryWrapper 查询条件
     * @return 操作结果
     */
    Result<Boolean> delete(QueryWrapper<T> queryWrapper);

    // ==================== 保存或更新方法（返回Result包装 + 图同步） ====================

    /**
     * 保存或更新
     *
     * @param entity 实体
     * @return 操作结果
     */
    Result<T> saveOrUpdateReturn(T entity);


    /**
     * 批量保存或更新并返回成功操作的实体列表
     *
     * @param entityList 实体列表
     * @return 成功操作的实体列表
     */
    Result<List<T>> saveOrUpdateBatchAndReturn(Collection<T> entityList);


    /**
     * 批量保存或更新（分批次）并返回成功操作的实体列表
     *
     * @param entityList 实体列表
     * @param batchSize 每批数量
     * @return 成功操作的实体列表
     */
    Result<List<T>> saveOrUpdateBatchAndReturn(Collection<T> entityList, int batchSize);

    // ==================== 链式查询方法 ====================


    // ==================== SQL投影查询（返回Result包装） ====================

    /**
     * 查询指定字段列表
     *
     * @param queryWrapper 查询条件
     * @param <V> 字段类型
     * @return 字段值列表
     */
    <V> Result<List<V>> listObjs(LambdaQueryWrapper<T> queryWrapper);

    /**
     * 查询指定字段列表
     *
     * @param queryWrapper 查询条件
     * @param <V> 字段类型
     * @return 字段值列表
     */
    <V> Result<List<V>> listObjs(QueryWrapper<T> queryWrapper);

    /**
     * 查询所有指定字段
     *
     * @param <V> 字段类型
     * @return 字段值列表
     */
    <V> Result<List<V>> listObjsAll();
}
