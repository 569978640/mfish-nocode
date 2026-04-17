package cn.com.mfish.plm.base.bean.folder.service;

import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.plm.base.bean.folder.entity.Folder;
import cn.com.mfish.plm.base.bean.folder.req.ReqFolder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * @description: 文件夹
 * @author: mfish
 * @date: 2026-04-17
 * @version: V2.3.1
 */
public interface FolderService extends IService<Folder> {
    /**
     * 分页列表查询
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @return 返回文件夹-分页列表
     */
    Result<PageResult<Folder>> queryPageList(ReqFolder reqFolder, ReqPage reqPage);

    /**
     * 添加
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-添加结果
     */
    Result<Folder> add(Folder folder);

    /**
     * 编辑
     *
     * @param folder 文件夹对象
     * @return 返回文件夹-编辑结果
     */
    Result<Folder> edit(Folder folder);

    /**
     * 通过id删除
     *
     * @param id 唯一ID
     * @return 返回文件夹-删除结果
     */
    Result<Boolean> delete(String id);

    /**
     * 批量删除
     *
     * @param ids 批量ID
     * @return 返回文件夹-删除结果
     */
    Result<Boolean> deleteBatch(String ids);

    /**
     * 通过id查询
     *
     * @param id 唯一ID
     * @return 返回文件夹对象
     */
    Result<Folder> queryById(String id);

    /**
     * 导出
     *
     * @param reqFolder 文件夹请求参数
     * @param reqPage 分页参数
     * @throws IOException IO异常
     */
    void export(ReqFolder reqFolder, ReqPage reqPage) throws IOException;
}
