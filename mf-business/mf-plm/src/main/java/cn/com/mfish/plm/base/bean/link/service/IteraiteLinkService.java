package cn.com.mfish.plm.base.bean.link.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.IteraiteLink;
import cn.com.mfish.plm.base.bean.link.req.ReqIteraiteLink;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 迭代关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface IteraiteLinkService extends IService<IteraiteLink> {
    /**
     * 分页列表查询
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @return 返回迭代关系-分页列表
     */
    Result<PageResult<IteraiteLink>> queryPageList(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage);

    /**
     * 添加
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-添加结果
     */
    Result<IteraiteLink> add(IteraiteLink iteraiteLink);

    /**
     * 编辑
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-编辑结果
     */
    Result<IteraiteLink> edit(IteraiteLink iteraiteLink);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回迭代关系-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回迭代关系-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回迭代关系对象
     */
    Result<IteraiteLink> queryById(String id);

    /**
     * 导出
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) throws IOException;
}
