package cn.com.mfish.plm.base.bean.part.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.WPartMaster;
import cn.com.mfish.plm.base.bean.part.req.ReqPartMaster;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 部件主数据
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface PartMasterService extends IService<WPartMaster> {
    /**
     * 分页列表查询
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @return 返回部件主数据-分页列表
     */
    Result<PageResult<WPartMaster>> queryPageList(ReqPartMaster reqPartMaster, ReqPage reqPage);

    /**
     * 添加
     *
     * @param wPartMaster 部件主数据对象
     * @return 返回部件主数据-添加结果
     */
    Result<WPartMaster> add(WPartMaster wPartMaster);

    /**
     * 编辑
     *
     * @param wPartMaster 部件主数据对象
     * @return 返回部件主数据-编辑结果
     */
    Result<WPartMaster> edit(WPartMaster wPartMaster);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件主数据-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件主数据-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件主数据对象
     */
    Result<WPartMaster> queryById(String id);

    /**
     * 导出
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqPartMaster reqPartMaster, ReqPage reqPage) throws IOException;
}
