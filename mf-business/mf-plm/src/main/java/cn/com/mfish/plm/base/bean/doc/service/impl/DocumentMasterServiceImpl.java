package cn.com.mfish.plm.base.bean.doc.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.doc.entity.DocumentMaster;
import cn.com.mfish.plm.base.bean.doc.req.ReqDocumentMaster;
import cn.com.mfish.plm.base.bean.doc.mapper.DocumentMasterMapper;
import cn.com.mfish.plm.base.bean.doc.service.DocumentMasterService;
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
* @description: 文档主数据
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class DocumentMasterServiceImpl extends ServiceImpl<DocumentMasterMapper, DocumentMaster> implements DocumentMasterService {
    /**
     * 分页列表查询
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @return 返回文档主数据-分页列表
     */
    @Override
    public Result<PageResult<DocumentMaster>> queryPageList(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqDocumentMaster, reqPage)), "文档主数据-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @return 返回文档主数据-分页列表
     */
    private List<DocumentMaster> queryList(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<DocumentMaster> lambdaQueryWrapper = new LambdaQueryWrapper<DocumentMaster>()
                .like(!StringUtils.isEmpty(reqDocumentMaster.getNumber()), DocumentMaster::getNumber, reqDocumentMaster.getNumber())
                        .like(!StringUtils.isEmpty(reqDocumentMaster.getName()), DocumentMaster::getName, reqDocumentMaster.getName())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-添加结果
     */
    @Override
    public Result<DocumentMaster> add(DocumentMaster documentMaster) {
        if (save(documentMaster)) {
            return Result.ok(documentMaster, "文档主数据-添加成功!");
        }
        return Result.fail(documentMaster, "错误:文档主数据-添加失败!");
    }

    /**
     * 编辑
     *
     * @param documentMaster 文档主数据对象
     * @return 返回文档主数据-编辑结果
     */
    @Override
    public Result<DocumentMaster> edit(DocumentMaster documentMaster) {
        if (updateById(documentMaster)) {
            return Result.ok(documentMaster, "文档主数据-编辑成功!");
        }
        return Result.fail(documentMaster, "错误:文档主数据-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文档主数据-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "文档主数据-删除成功!");
        }
        return Result.fail(false, "错误:文档主数据-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文档主数据-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "文档主数据-批量删除成功!");
        }
        return Result.fail(false, "错误:文档主数据-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文档主数据对象
     */
    @Override
    public Result<DocumentMaster> queryById(String id) {
        DocumentMaster documentMaster = getById(id);
        return Result.ok(documentMaster, "文档主数据-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqDocumentMaster 文档主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqDocumentMaster reqDocumentMaster, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("文档主数据_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqDocumentMaster, reqPage));
    }
}
