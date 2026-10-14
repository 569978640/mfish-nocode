package cn.com.mfish.bfces.pbi.service;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;

import java.util.List;

/**
 * @description: PBI服务
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.3.0
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

    /**
     * 获取产品家谱树。
     * 以二级分类下的底层部件为起点，沿"上一代物料编码"逐层向上递归，BASELINE 为顶层；
     * 属性值为空、属性行缺失或父部件编号无法解析的支系不进入家谱。
     *
     * @param productClassification 二级分类内部名称（PRODUCTCLASSIFICATION）
     * @return 产品家谱顶层节点；无有效支系时返回null
     */
    PbiFamilyTreeNode getFamilyTree(String productClassification);

    /**
     * 按部件编号获取单个产品型号完整信息（产品家谱点击部件编号进入"详细特性参数"页使用）。
     * 特性参数列取该一级分类下配置的展示列，部件编号全库唯一，不限制二级分类。
     *
     * @param firstCategory 一级分类（决定特性参数展示列）
     * @param wtPartNumber  部件编号
     * @return 产品型号；编号为空或未查到时返回null
     */
    PbiPart getPartDetail(String firstCategory, String wtPartNumber);

    /**
     * 应急运维：清空Redis中全部PBI部件相关缓存（部件属性Hash、分类/编号ID索引）。
     * 清空后下次查询将按实时ID列表自动从Oracle差量重建，无需重启服务。
     *
     * @return 实际删除的缓存key数量
     */
    long refreshPartCache();
}
