package cn.com.mfish.plm.base.bean.doc.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.DocumentMaster;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocumentMaster;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 文档主数据
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface DocumentMasterService extends IService<DocumentMaster> {
    /**
     * 分页列表查询
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @return 返回文档主数据-分页列表
     */
    Result<PageResult<DocumentMaster>> queryPageList(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage);

    /**
     * 添加
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-添加结果
     */
    Result<DocumentMaster> add(DocumentMaster documentMaster);

    /**
     * 编辑
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-编辑结果
     */
    Result<DocumentMaster> edit(DocumentMaster documentMaster);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档主数据-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档主数据-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档主数据对象
     */
    Result<DocumentMaster> queryById(String id);

    /**
     * 导出
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) throws IOException;
}
