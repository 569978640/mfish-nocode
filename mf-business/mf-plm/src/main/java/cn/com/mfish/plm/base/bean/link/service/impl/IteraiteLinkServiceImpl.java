package cn.com.mfish.plm.base.bean.link.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.IteraiteLink;
import cn.com.mfish.plm.base.bean.link.req.ReqIteraiteLink;
import cn.com.mfish.plm.base.bean.link.mapper.IteraiteLinkMapper;
import cn.com.mfish.plm.base.bean.link.service.IteraiteLinkService;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 迭代关系
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class IteraiteLinkServiceImpl extends ServiceImpl<IteraiteLinkMapper, IteraiteLink> implements IteraiteLinkService {
    /**
     * 分页列表查询
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @return 返回迭代关系-分页列表
     */
    @Override
    public Result<PageResult<IteraiteLink>> queryPageList(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqIteraiteLink, reqPage)), "迭代关系-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @return 返回迭代关系-分页列表
     */
    private List<IteraiteLink> queryList(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        return list();
    }

    /**
     * 添加
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-添加结果
     */
    @Override
    public Result<IteraiteLink> add(IteraiteLink iteraiteLink) {
        if (save(iteraiteLink)) {
            return Result.ok(iteraiteLink, "迭代关系-添加成功!");
        }
        return Result.fail(iteraiteLink, "错误:迭代关系-添加失败!");
    }

    /**
     * 编辑
     *
     * @param iteraiteLink 迭代关系对象
     * @return 返回迭代关系-编辑结果
     */
    @Override
    public Result<IteraiteLink> edit(IteraiteLink iteraiteLink) {
        if (updateById(iteraiteLink)) {
            return Result.ok(iteraiteLink, "迭代关系-编辑成功!");
        }
        return Result.fail(iteraiteLink, "错误:迭代关系-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回迭代关系-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "迭代关系-删除成功!");
        }
        return Result.fail(false, "错误:迭代关系-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回迭代关系-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "迭代关系-批量删除成功!");
        }
        return Result.fail(false, "错误:迭代关系-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回迭代关系对象
     */
    @Override
    public Result<IteraiteLink> queryById(String id) {
        IteraiteLink iteraiteLink = getById(id);
        return Result.ok(iteraiteLink, "迭代关系-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqIteraiteLink 迭代关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqIteraiteLink reqIteraiteLink, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("迭代关系_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqIteraiteLink, reqPage));
    }
}
