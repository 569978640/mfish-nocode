package cn.com.mfish.oauth.controller;

import cn.com.mfish.common.core.annotation.InnerUser;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.log.annotation.Log;
import cn.com.mfish.common.oauth.annotation.RequiresPermissions;
import cn.com.mfish.common.oauth.api.entity.SsoMenu;
import cn.com.mfish.common.oauth.api.req.ReqSsoMenu;
import cn.com.mfish.common.oauth.api.vo.MenuRouteVo;
import cn.com.mfish.common.oauth.service.SsoMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Description: 菜单表
 * @Author: mfish
 * @date: 2022-09-21
 * @version: V2.4.1
 */
@Slf4j
@Tag(name = "菜单表")
@RestController
@RequestMapping("/menu")
public class SsoMenuController {
    @Resource
    SsoMenuService ssoMenuService;

    /**
     * 查询菜单树
     *
     * @param reqSsoMenu 请求参数
     * @return 菜单列表
     */
    @Operation(summary = "菜单表-查询菜单树", description = "菜单表-查询菜单树")
    @GetMapping
    @RequiresPermissions("sys:menu:query")
    public Result<List<SsoMenu>> queryList(ReqSsoMenu reqSsoMenu) {
        return ssoMenuService.queryMenuTree(reqSsoMenu, null);
    }

    /**
     * 获取所有菜单树（不分页）
     *
     * @param reqSsoMenu 菜单查询参数
     * @return 菜单树列表
     */
    @Operation(summary = "获取所有菜单树")
    @GetMapping("/tree")
    public Result<List<SsoMenu>> queryMenuTree(ReqSsoMenu reqSsoMenu) {
        return ssoMenuService.queryMenuTree(reqSsoMenu, null);
    }

    /**
     * 获取当前用户的角色菜单树（左侧菜单，不含按钮）
     *
     * @return 菜单树列表
     */
    @Operation(summary = "获取角色树-左侧菜单")
    @GetMapping("/roleTree")
    public Result<List<SsoMenu>> queryRoleMenuTree() {
        return ssoMenuService.queryMenuTree(new ReqSsoMenu().setNoButton(true), AuthInfoUtils.getCurrentUserId());
    }

    /**
     * 添加菜单
     *
     * @param ssoMenu 菜单对象
     * @return 返回添加结果
     */
    @Log(title = "菜单表-添加", operateType = OperateType.INSERT)
    @Operation(summary = "菜单表-添加", description = "菜单表-添加")
    @PostMapping
    @RequiresPermissions("sys:menu:insert")
    public Result<SsoMenu> add(@RequestBody SsoMenu ssoMenu) {
        return ssoMenuService.insertMenu(ssoMenu);
    }

    /**
     * 编辑菜单
     *
     * @param ssoMenu 菜单对象
     * @return 返回编辑结果
     */
    @Log(title = "菜单表-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "菜单表-编辑", description = "菜单表-编辑")
    @PutMapping
    @RequiresPermissions("sys:menu:update")
    public Result<SsoMenu> edit(@RequestBody SsoMenu ssoMenu) {
        return ssoMenuService.updateMenu(ssoMenu);
    }

    /**
     * 通过id删除菜单
     *
     * @param id 菜单唯一ID
     * @return 返回删除结果
     */
    @Log(title = "菜单表-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "菜单表-通过id删除", description = "菜单表-通过id删除")
    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:menu:delete")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
        return ssoMenuService.deleteMenu(id);
    }

    /**
     * 通过id查询菜单信息
     *
     * @param id 菜单唯一ID
     * @return 菜单对象
     */
    @Operation(summary = "菜单表-通过id查询", description = "菜单表-通过id查询")
    @GetMapping("/{id}")
    @RequiresPermissions("sys:menu:query")
    public Result<SsoMenu> queryById(@Parameter(name = "id", description = "唯一性ID") @PathVariable String id) {
        SsoMenu ssoMenu = ssoMenuService.getById(id);
        return Result.ok(ssoMenu, "菜单表-查询成功!");
    }

    /**
     * 判断路由地址是否已存在（内部接口）
     *
     * @param routePath 路由地址
     * @param parentId  父菜单ID
     * @return 返回路由是否已存在
     */
    @Operation(summary = "判断路由是否存在")
    @GetMapping("/routeExist")
    @InnerUser
    @Parameter(name = "路由地址", required = true)
    public Result<Boolean> routeExist(@RequestParam String routePath, @RequestParam String parentId) {
        return ssoMenuService.routeExist(routePath, parentId);
    }

    /**
     * 获取所有菜单的完整路由地址（子菜单路由拼接父菜单路由）
     * <p>
     * 查询所有目录和菜单（排除按钮），子菜单的 routePath 会拼接父菜单的 routePath，
     * 形成完整的路由地址。例如：父菜单 /system + 子菜单 /menu → /system/menu。
     * </p>
     * <p>
     * 主要供 AI 前端操作工具（frontend.navigate）查询可用路由列表使用，
     * 也可用于前端路由校验等场景。
     * </p>
     *
     * @return 路由地址列表
     */
    @Operation(summary = "获取路由地址", description = "查询菜单的完整路由地址（含菜单名称），子菜单路由拼接父菜单路由。支持多个关键词（逗号分隔，OR 匹配），keyword 为空返回全部。供 AI 导航工具查询可用路由使用")
    @GetMapping("/routePaths")
    public Result<List<MenuRouteVo>> queryRoutePaths(
            @Parameter(description = "菜单名称关键词（可选，支持逗号分隔的多关键词 OR 匹配，如 '大屏,可视化,screen'，为空返回全部）", example = "大屏,可视化")
            @RequestParam(required = false) String keyword) {
        return ssoMenuService.queryRoutePaths(keyword);
    }
}
