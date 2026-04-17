package cn.com.mfish.plm.base.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.Serializable;
import java.util.List;

/**
 * PLM基础服务接口
 * 继承MyBatis-Plus IService，提供完整的CRUD操作和图同步功能
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

    /**
     * 分页列表查询
     *
     * @param reqPage 分页参数
     * @return 分页结果
     */
    Result<PageResult<T>> queryPageList(ReqPage reqPage);

    /**
     * 添加
     *
     * @param entity 实体对象
     * @return 操作结果
     */
    Result<T> add(T entity);

    /**
     * 批量添加
     *
     * @param list 实体列表
     * @return 操作结果
     */
    Result<Boolean> addBatch(List<T> list);

    /**
     * 编辑
     *
     * @param entity 实体对象
     * @return 操作结果
     */
    Result<T> update(T entity);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 操作结果
     */
    Result<Boolean> delete(Serializable id);

    /**
     * 批量删除
     *
     * @param ids 批量ID（逗号分隔）
     * @return 操作结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 查询结果
     */
    Result<T> queryById(Serializable id);

    /**
     * 根据ID列表查询
     *
     * @param ids ID列表
     * @return 实体列表
     */
    Result<List<T>> queryByIds(List<Serializable> ids);

    /**
     * 根据条件查询列表
     *
     * @param wrapper 查询条件
     * @return 实体列表
     */
    Result<List<T>> queryList(LambdaQueryWrapper<T> wrapper);

    /**
     * 保存或更新
     *
     * @param entity 实体对象
     * @return 操作结果
     */
    Result<T> saveOrUpdateEntity(T entity);

    /**
     * 批量保存或更新
     *
     * @param list 实体列表
     * @return 操作结果
     */
    Result<Boolean> saveOrUpdateBatch(List<T> list);
}
