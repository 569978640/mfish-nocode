package cn.com.mfish.bfces.pbi.controller;

import cn.com.mfish.bfces.pbi.entity.PbiNode;
import cn.com.mfish.bfces.pbi.service.PBIService;
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
 * @version: V1.0.0
 */
@Slf4j
@Tag(name = "PBI接口")
@RestController
@RequestMapping("/pbi")
public class PBIController {

    @Resource
    private PBIService pbiService;

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
}
