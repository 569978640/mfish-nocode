package cn.com.mfish.plm.base.bean.link.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.ContainsLink;
import cn.com.mfish.plm.base.bean.link.req.ReqContainsLink;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 包含关系
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface ContainsLinkService extends IService<ContainsLink> {
    /**
     * 分页列表查询
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @return 返回包含关系-分页列表
     */
    Result<PageResult<ContainsLink>> queryPageList(ReqContainsLink reqContainsLink, ReqPage reqPage);

    /**
     * 添加
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-添加结果
     */
    Result<ContainsLink> add(ContainsLink containsLink);

    /**
     * 编辑
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-编辑结果
     */
    Result<ContainsLink> edit(ContainsLink containsLink);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回包含关系-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回包含关系-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回包含关系对象
     */
    Result<ContainsLink> queryById(String id);

    /**
     * 导出
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqContainsLink reqContainsLink, ReqPage reqPage) throws IOException;
}
