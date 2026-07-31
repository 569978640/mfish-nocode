package cn.com.mfish.common.oauth.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 菜单路由地址 VO
 * <p>
 * 包含菜单名称和完整路由地址（子菜单路由拼接父菜单路由），
 * 供 AI 识别应该采用哪个路由。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/24
 */
@Data
@Accessors(chain = true)
@Schema(description = "菜单路由地址")
public class MenuRouteVo {

    @Schema(description = "菜单名称")
    private String menuName;

    @Schema(description = "完整路由地址（子菜单路由拼接父菜单路由）")
    private String routePath;

    public MenuRouteVo() {
    }

    public MenuRouteVo(String menuName, String routePath) {
        this.menuName = menuName;
        this.routePath = routePath;
    }
}
