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
 * @version: V1.2.0
 */
public interface PbiService {
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
     * 获取产品型号下拉选项（竞品选择级联下拉用）
     * 仅查询编号、名称两列，每项格式为"部件编号,部件名称"
     *
     * @param firstCategory 一级分类
     * @param productClassification 产品分类（二级分类内部名称）
     * @return 下拉选项字符串列表
     */
    List<String> getPartOptions(String firstCategory, String productClassification);

    /**
     * 获取筛选属性信息
     *
     * @param nodeName 节点名称(可选)
     * @return 返回筛选属性列表
     */
    List<PbiFilterProperty> getFilterProperties(String nodeName);
}
