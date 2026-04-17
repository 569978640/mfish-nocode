package cn.com.mfish.plm.base.bean.part.service.impl;

import cn.com.mfish.common.core.utils.excel.ExcelUtils;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.part.entity.PartMaster;
import cn.com.mfish.plm.base.bean.part.req.ReqPartMaster;
import cn.com.mfish.plm.base.bean.part.mapper.PartMasterMapper;
import cn.com.mfish.plm.base.bean.part.service.PartMasterService;
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
* @description: 部件主数据
* @author: mfish
* @date: 2026-04-17
* @version: V2.3.1
*/
@Service
public class PartMasterServiceImpl extends ServiceImpl<PartMasterMapper, PartMaster> implements PartMasterService {
    /**
     * 分页列表查询
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @return 返回部件主数据-分页列表
     */
    @Override
    public Result<PageResult<PartMaster>> queryPageList(ReqPartMaster reqPartMaster, ReqPage reqPage) {
        return Result.ok(new PageResult<>(queryList(reqPartMaster, reqPage)), "部件主数据-查询成功!");
    }

    /**
     * 获取列表
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @return 返回部件主数据-分页列表
     */
    private List<PartMaster> queryList(ReqPartMaster reqPartMaster, ReqPage reqPage) {
    PageHelper.startPage(reqPage.getPageNum(), reqPage.getPageSize());
        LambdaQueryWrapper<PartMaster> lambdaQueryWrapper = new LambdaQueryWrapper<PartMaster>()
                .eq(!StringUtils.isEmpty(reqPartMaster.getNumber()), PartMaster::getNumber, reqPartMaster.getNumber())
                        .eq(!StringUtils.isEmpty(reqPartMaster.getName()), PartMaster::getName, reqPartMaster.getName())
        ;
        return list(lambdaQueryWrapper);
    }

    /**
     * 添加
     *
     * @param partMaster 部件主数据对象
     * @return 返回部件主数据-添加结果
     */
    @Override
    public Result<PartMaster> add(PartMaster partMaster) {
        if (save(partMaster)) {
            return Result.ok(partMaster, "部件主数据-添加成功!");
        }
        return Result.fail(partMaster, "错误:部件主数据-添加失败!");
    }

    /**
     * 编辑
     *
     * @param partMaster 部件主数据对象
     * @return 返回部件主数据-编辑结果
     */
    @Override
    public Result<PartMaster> edit(PartMaster partMaster) {
        if (updateById(partMaster)) {
            return Result.ok(partMaster, "部件主数据-编辑成功!");
        }
        return Result.fail(partMaster, "错误:部件主数据-编辑失败!");
    }

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回部件主数据-删除结果
     */
    @Override
    public Result<Boolean> delete(String id) {
        if (removeById(id)) {
            return Result.ok(true, "部件主数据-删除成功!");
        }
        return Result.fail(false, "错误:部件主数据-删除失败!");
    }

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回部件主数据-删除结果
     */
    @Override
    public Result<Boolean> deleteBatch(String ids) {
        if (removeByIds(Arrays.asList(ids.split(",")))) {
            return Result.ok(true, "部件主数据-批量删除成功!");
        }
        return Result.fail(false, "错误:部件主数据-批量删除失败!");
    }

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回部件主数据对象
     */
    @Override
    public Result<PartMaster> queryById(String id) {
        PartMaster partMaster = getById(id);
        return Result.ok(partMaster, "部件主数据-查询成功!");
    }

    /**
     * 导出
     *
     * @param reqPartMaster 部件主数据请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    @Override
    public void export(ReqPartMaster reqPartMaster, ReqPage reqPage) throws IOException {
        //swagger调用会用问题，使用postman测试
        ExcelUtils.write("部件主数据_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()), queryList(reqPartMaster, reqPage));
    }
}
