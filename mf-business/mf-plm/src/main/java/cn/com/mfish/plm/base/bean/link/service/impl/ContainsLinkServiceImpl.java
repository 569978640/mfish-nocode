package cn.com.mfish.plm.base.bean.link.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.link.entity.ContainsLink;
import cn.com.mfish.plm.base.bean.link.req.ReqContainsLink;
import cn.com.mfish.plm.base.bean.link.mapper.ContainsLinkMapper;
import cn.com.mfish.plm.base.bean.link.service.ContainsLinkService;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.io.IOException;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
* @description: 包含关系
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class ContainsLinkServiceImpl extends ServiceImpl<ContainsLinkMapper, ContainsLink> implements ContainsLinkService {
    /**
     * 分页列表查询
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @return 返回包含关系-分页列表
     */
    @Override
    public Result<PageResult<ContainsLink>> queryPageList(ReqContainsLink reqContainsLink, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqContainsLink, reqPage)), "包含关系-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @return 返回包含关系-分页列表
     */
    private List<ContainsLink> queryList(ReqContainsLink reqContainsLink, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        return list();
    }

    /**
     * 添加
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-添加结果
     */
    @Override
    public Result<ContainsLink> add(ContainsLink containsLink) {
        if (save(containsLink)) {
            return Result.ok(containsLink, "包含关系-添加成功!");
        }
        return Result.fail(containsLink, "错误:包含关系-添加失败!");
    }

    /**
     * 编辑
     *
     * @param containsLink 包含关系对象
     * @return 返回包含关系-编辑结果
     */
    @Override
    public Result<ContainsLink> edit(ContainsLink containsLink) {
        if (updateById(containsLink)) {
            return Result.ok(containsLink, "包含关系-编辑成功!");
        }
        return Result.fail(containsLink, "错误:包含关系-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回包含关系-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "包含关系-删除成功!");
        }
        return Result.fail(false, "错误:包含关系-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回包含关系-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "包含关系-批量删除成功!");
        }
        return Result.fail(false, "错误:包含关系-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回包含关系对象
     */
    @Override
    public Result<ContainsLink> queryById(String id) {
        ContainsLink containsLink = getById(id);
        return Result.ok(containsLink, "包含关系-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqContainsLink 包含关系请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqContainsLink reqContainsLink, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("包含关系_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqContainsLink, reqPage));
    }
}
