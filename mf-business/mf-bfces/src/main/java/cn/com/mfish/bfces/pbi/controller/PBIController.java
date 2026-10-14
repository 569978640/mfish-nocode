package cn.com.mfish.bfces.pbi.controller;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import cn.com.mfish.bfces.pbi.entity.PbiFilterProperty;
import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.entity.PbiParamConfig;
import cn.com.mfish.bfces.pbi.entity.PbiPart;
import cn.com.mfish.bfces.pbi.service.PbiService;
import cn.com.mfish.common.core.web.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @description: PBI控制器
 * @author: mfish
 * @date: 2026-10-09
 * @version: V1.4.0
 */
@Slf4j
@Tag(name = "PBI接口")
@RestController
@RequestMapping("/pbi")
public class PbiController {

    @Resource
    private PbiService pbiService;

    /**
     * 获取一级分类
     *
     * @return 返回一级分类列表
     */
    @Operation(summary = "获取一级分类", description = "获取PBI一级分类列表")
    @GetMapping("/node1")
    public Result<List<PbiNode>> findNode1() {
        return Result.ok(pbiService.findNode1(), "获取一级分类成功!");
    }

    /**
     * 获取二级分类
     *
     * @param parentNodeName 父节点名称
     * @return 返回二级分类列表
     */
    @Operation(summary = "获取二级分类", description = "获取PBI二级分类列表")
    @GetMapping("/node2")
    public Result<List<PbiNode>> findNode2(@RequestParam String parentNodeName) {
        return Result.ok(pbiService.findNode2(parentNodeName), "获取二级分类成功!");
    }

    /**
     * 获取PBI属性配置
     *
     * @param firstCategory 一级分类名称
     * @return 返回属性配置列表
     */
    @Operation(summary = "获取属性配置", description = "获取PBI特征参数展示配置")
    @GetMapping("/paramConfig")
    public Result<List<PbiParamConfig>> getParamConfig(@RequestParam("firstCategory") String firstCategory) {
        return Result.ok(pbiService.getParamConfig(firstCategory), "获取属性配置成功!");
    }

    /**
     * 获取产品型号
     *
     * @param firstCategory 一级分类
     * @param productClassification 产品分类
     * @return 返回产品型号列表
     */
    @Operation(summary = "获取产品型号", description = "根据产品分类获取产品型号列表")
    @GetMapping("/part")
    public Result<List<PbiPart>> getPartsByClassification(
            @RequestParam("firstCategory") String firstCategory,
            @RequestParam("productClassification") String productClassification) {
        return Result.ok(pbiService.getPartsByClassification(firstCategory, productClassification), "获取产品型号成功!");
    }

    /**
     * 获取产品型号下拉选项
     *
     * @param firstCategory 一级分类
     * @param productClassification 产品分类
     * @return 返回"部件编号,部件名称"下拉选项列表
     */
    @Operation(summary = "获取产品型号下拉选项", description = "根据产品分类获取部件编号+名称下拉选项列表")
    @GetMapping("/partOptions")
    public Result<List<String>> getPartOptions(
            @RequestParam("firstCategory") String firstCategory,
            @RequestParam("productClassification") String productClassification) {
        return Result.ok(pbiService.getPartOptions(firstCategory, productClassification), "获取产品型号选项成功!");
    }

    /**
     * 获取筛选属性
     *
     * @param nodeName 节点名称(可选)
     * @return 返回筛选属性列表
     */
    @Operation(summary = "获取筛选属性", description = "获取PBI筛选属性列表")
    @GetMapping("/filterProperties")
    public Result<List<PbiFilterProperty>> getFilterProperties(@RequestParam(required = false) String nodeName) {
        return Result.ok(pbiService.getFilterProperties(nodeName), "获取筛选属性成功!");
    }

    /**
     * 获取产品家谱树
     *
     * @param productClassification 二级分类内部名称（PRODUCTCLASSIFICATION）
     * @return 返回产品家谱顶层节点（自底向上递归组装的衍生关系树）
     */
    @Operation(summary = "获取产品家谱", description = "根据二级分类自底向上递归查询并组装产品家谱树")
    @GetMapping("/familyTree")
    public Result<PbiFamilyTreeNode> getFamilyTree(@RequestParam("productClassification") String productClassification) {
        return Result.ok(pbiService.getFamilyTree(productClassification), "获取产品家谱成功!");
    }

    /**
     * 按部件编号获取单个产品型号详情（详细特性参数）
     *
     * @param firstCategory 一级分类（决定特性参数展示列）
     * @param wtPartNumber  部件编号
     * @return 产品型号详情；未查到时 data 为 null
     */
    @Operation(summary = "获取产品型号详情", description = "根据部件编号获取单个产品型号完整特性参数")
    @GetMapping("/partDetail")
    public Result<PbiPart> getPartDetail(
            @RequestParam("firstCategory") String firstCategory,
            @RequestParam("wtPartNumber") String wtPartNumber) {
        return Result.ok(pbiService.getPartDetail(firstCategory, wtPartNumber), "获取产品型号详情成功!");
    }
}
