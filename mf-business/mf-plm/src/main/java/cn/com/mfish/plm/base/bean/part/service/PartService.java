package cn.com.mfish.plm.base.bean.part.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.WPart;
import cn.com.mfish.plm.base.bean.part.req.ReqPart;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 部件小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface PartService extends IService<WPart> {
    /**
     * 分页列表查询
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @return 返回部件小版本-分页列表
     */
    Result<PageResult<WPart>> queryPageList(ReqPart reqPart, ReqPage reqPage);

    /**
     * 添加
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-添加结果
     */
    Result<WPart> add(WPart wPart);

    /**
     * 编辑
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-编辑结果
     */
    Result<WPart> edit(WPart wPart);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件小版本-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件小版本-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件小版本对象
     */
    Result<WPart> queryById(String id);

    /**
     * 导出
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqPart reqPart, ReqPage reqPage) throws IOException;
}
