package cn.com.mfish.common.ai.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 前端操作指令 DTO
 * <p>
 * 当 LLM 调用 {@code FrontendActionTool} 时，生成此对象并通过 SSE
 * {@link EventType#FRONTEND_ACTION} 事件下发给前端。前端 Action Handler
 * 消费此对象并执行对应的 UI 操作（路由跳转、按钮点击、表单填充等）。
 * </p>
 * <p>
 * <b>支持的 action 类型</b>：
 * <ul>
 *   <li>{@code navigate} — 路由跳转，target 为路由路径（如 /demo-leave-apply），params 可携带 query 参数</li>
 *   <li>{@code click} — 模拟点击，target 为元素选择器（如 #submit-btn）</li>
 *   <li>{@code fill} — 表单填充，target 为表单选择器，params 为字段名→值的映射</li>
 *   <li>{@code refresh} — 刷新当前页面或指定组件，target 可选</li>
 *   <li>{@code openModal} — 打开模态框，target 为模态框标识，params 携带模态框参数</li>
 * </ul>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/24
 */
@Data
@Accessors(chain = true)
public class FrontendAction {

    /** 路由跳转 */
    public static final String NAVIGATE = "navigate";
    /** 模拟点击 */
    public static final String CLICK = "click";
    /** 表单填充 */
    public static final String FILL = "fill";
    /** 刷新页面/组件 */
    public static final String REFRESH = "refresh";
    /** 打开模态框 */
    public static final String OPEN_MODAL = "openModal";

    /** 操作类型：navigate / click / fill / refresh / openModal */
    private String action;
    /** 工具名（如 frontend.navigate、frontend.refresh），用于判断是否延迟下发 */
    private String name;
    /** 操作目标：路由路径、元素选择器、模态框标识等 */
    private String target;
    /** 操作参数：query 参数、表单字段值、模态框参数等 */
    private Map<String, Object> params;
    /** 操作描述：供前端展示给用户的可读说明（如"已为您打开请假申请页面"） */
    private String description;

    public FrontendAction() {
    }

    public FrontendAction(String action, String target) {
        this.action = action;
        this.target = target;
    }

    public FrontendAction(String action, String target, Map<String, Object> params) {
        this.action = action;
        this.target = target;
        this.params = params;
    }
}
