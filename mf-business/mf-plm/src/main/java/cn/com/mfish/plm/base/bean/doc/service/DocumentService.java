package cn.com.mfish.plm.base.bean.doc.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.Document;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocument;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 文档小版本
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface DocumentService extends IService<Document> {
    /**
     * 分页列表查询
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @return 返回文档小版本-分页列表
     */
    Result<PageResult<Document>> queryPageList(ReqDocument reqDocument, ReqPage reqPage);

    /**
     * 添加
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-添加结果
     */
    Result<Document> add(Document document);

    /**
     * 编辑
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-编辑结果
     */
    Result<Document> edit(Document document);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档小版本-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档小版本-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档小版本对象
     */
    Result<Document> queryById(String id);

    /**
     * 导出
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqDocument reqDocument, ReqPage reqPage) throws IOException;
}
