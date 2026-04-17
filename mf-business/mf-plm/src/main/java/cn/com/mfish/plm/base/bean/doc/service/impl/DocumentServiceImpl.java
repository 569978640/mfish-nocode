package cn.com.mfish.plm.base.bean.doc.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.Document;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocument;
import cn.com.mfish.plm.base.bean.doc.mapper.DocumentMapper;
import cn.com.mfish.plm.base.bean.doc.service.DocumentService;
import cn.com.mfish.common.core.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 文档小版本
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
    /**
     * 分页列表查询
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @return 返回文档小版本-分页列表
     */
    @Override
    public Result<PageResult<Document>> queryPageList(ReqDocument reqDocument, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqDocument, reqPage)), "文档小版本-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @return 返回文档小版本-分页列表
     */
    private List<Document> queryList(ReqDocument reqDocument, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<Document> lambdaQueryWrapper = new LambdaQueryWrapper<Document>()
                .eq(!StringUtils.isEmpty(reqDocument.getVersion()), Document::getVersion, reqDocument.getVersion())
                        .like(!StringUtils.isEmpty(reqDocument.getState()), Document::getState, reqDocument.getState())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-添加结果
     */
    @Override
    public Result<Document> add(Document document) {
        if (save(document)) {
            return Result.ok(document, "文档小版本-添加成功!");
        }
        return Result.fail(document, "错误:文档小版本-添加失败!");
    }

    /**
     * 编辑
     *
     * @param document 文档小版本对象
     * @return 返回文档小版本-编辑结果
     */
    @Override
    public Result<Document> edit(Document document) {
        if (updateById(document)) {
            return Result.ok(document, "文档小版本-编辑成功!");
        }
        return Result.fail(document, "错误:文档小版本-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档小版本-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "文档小版本-删除成功!");
        }
        return Result.fail(false, "错误:文档小版本-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档小版本-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "文档小版本-批量删除成功!");
        }
        return Result.fail(false, "错误:文档小版本-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档小版本对象
     */
    @Override
    public Result<Document> queryById(String id) {
        Document document = getById(id);
        return Result.ok(document, "文档小版本-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqDocument 文档小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqDocument reqDocument, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("文档小版本_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqDocument, reqPage));
    }
}
