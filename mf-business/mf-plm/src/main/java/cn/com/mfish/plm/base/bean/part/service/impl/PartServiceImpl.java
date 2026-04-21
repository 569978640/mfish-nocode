package cn.com.mfish.plm.base.bean.part.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.WPart;
import cn.com.mfish.plm.base.bean.part.req.ReqPart;
import cn.com.mfish.plm.base.bean.part.mapper.PartMapper;
import cn.com.mfish.plm.base.bean.part.service.PartService;
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
* @description: 部件小版本
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class PartServiceImpl extends ServiceImpl<PartMapper, WPart> implements PartService {
    /**
     * 分页列表查询
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @return 返回部件小版本-分页列表
     */
    @Override
    public Result<PageResult<WPart>> queryPageList(ReqPart reqPart, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqPart, reqPage)), "部件小版本-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @return 返回部件小版本-分页列表
     */
    private List<WPart> queryList(ReqPart reqPart, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<WPart> lambdaQueryWrapper = new LambdaQueryWrapper<WPart>()
                .eq(!StringUtils.isEmpty(reqPart.getState()), WPart::getState, reqPart.getState())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-添加结果
     */
    @Override
    public Result<WPart> add(WPart wPart) {
        if (save(wPart)) {
            return Result.ok(wPart, "部件小版本-添加成功!");
        }
        return Result.fail(wPart, "错误:部件小版本-添加失败!");
    }

    /**
     * 编辑
     *
     * @param wPart 部件小版本对象
     * @return 返回部件小版本-编辑结果
     */
    @Override
    public Result<WPart> edit(WPart wPart) {
        if (updateById(wPart)) {
            return Result.ok(wPart, "部件小版本-编辑成功!");
        }
        return Result.fail(wPart, "错误:部件小版本-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件小版本-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "部件小版本-删除成功!");
        }
        return Result.fail(false, "错误:部件小版本-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件小版本-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "部件小版本-批量删除成功!");
        }
        return Result.fail(false, "错误:部件小版本-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件小版本对象
     */
    @Override
    public Result<WPart> queryById(String id) {
        WPart wPart = getById(id);
        return Result.ok(wPart, "部件小版本-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqPart 部件小版本请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqPart reqPart, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("部件小版本_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqPart, reqPage));
    }
}
