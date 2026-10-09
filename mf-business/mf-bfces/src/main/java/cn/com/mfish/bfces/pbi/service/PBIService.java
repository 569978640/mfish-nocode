package cn.com.mfish.bfces.pbi.service;

import cn.com.mfish.bfces.pbi.entity.PbiNode;

import java.util.List;

/**
 * @description: PBI服务
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.0.0
 */
public interface PBIService {
    /**
     * 获取一级分类
     *
     * @return 返回一级分类列表
     */
    List<PbiNode> findNode1();

    /**
     * 获取二级分类
     *
     * @param parentNodeName 父节点名称
     * @return 返回二级分类列表
     */
    List<PbiNode> findNode2(String parentNodeName);
}
