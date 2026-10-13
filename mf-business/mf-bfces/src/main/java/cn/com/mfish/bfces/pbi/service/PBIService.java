package cn.com.mfish.bfces.pbi.service;

import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;

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

    /**
     * 获取PBI参数配置
     *
     * @param firstCategory 一级分类
     * @return 返回参数配置列表
     */
    List<PbiParamConfig> getParamConfig(String firstCategory);

    /**
     * 获取产品型号
     *
     * @param firstCategory 一级分类
     * @param productClassification 产品分类
     * @return 返回产品型号列表
     */
    List<PbiPart> getPartsByClassification(String firstCategory, String productClassification);

    /**
     * 获取筛选属性信息
     *
     * @param nodeName 节点名称(可选)
     * @return 返回筛选属性列表
     */
    List<PbiFilterProperty> getFilterProperties(String nodeName);
}
