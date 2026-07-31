package cn.com.mfish.common.ai.frontend;

import cn.com.mfish.common.ai.entity.FrontendAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 前端操作指令持有者
 * <p>
 * 基于 sessionId 的 {@link ConcurrentHashMap} 存储前端操作指令，
 * 解决响应式编程中 ThreadLocal 线程切换丢失数据的问题。
 * </p>
 * <p>
 * <b>双通道设计（实时 + 延迟）</b>：
 * <ul>
 *   <li><b>实时通道（emitter）</b>：未声明为延迟的工具通过 emitter 回调
 *       立即下发，与 token 流交织，实现"先跳转页面、再执行业务、最后反馈文本"</li>
 *   <li><b>延迟通道（list）</b>：Skill 声明为 deferredTools 的工具存入 list，
 *       延迟到主流（文本流）完成后通过 {@link #drain} 批量下发，
 *       实现"文本反馈后再刷新页面"</li>
 * </ul>
 * </p>
 * <p>
 * <b>哪些工具需要延迟？</b>由 Skill 的 frontmatter {@code deferredTools} 字段声明，
 * 而非硬编码。{@link cn.com.mfish.common.ai.capability.SkillCapabilityEngine} 在刷新时
 * 通过 {@link #setDeferredTools(Set)} 注入合并后的延迟工具名集合。
 * 例如 leave-apply.md 声明 {@code deferredTools: frontend.refresh}，
 * 则 frontend.refresh 的操作延迟到文本之后下发。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/24
 */
public class FrontendActionHolder {

    private static final ConcurrentHashMap<String, List<FrontendAction>> HOLDER = new ConcurrentHashMap<>();

    /** chat 模式的实时发射回调（按 sessionId） */
    private static final ConcurrentHashMap<String, Consumer<FrontendAction>> EMITTERS = new ConcurrentHashMap<>();

    /** ToolContext 中 sessionId 的 key（复用 RPCConstants 的约定） */
    public static final String CTX_SESSION_ID = "sessionId";

    /**
     * Skill 声明的延迟工具名集合（volatile 保证多线程可见性）
     * <p>
     * 由 {@link cn.com.mfish.common.ai.capability.SkillCapabilityEngine#refresh()} 在刷新后注入。
     * 集合中的元素是工具名（如 {@code frontend.refresh}），与 {@link FrontendAction#getName()} 匹配。
     * 默认空集合：无 Skill 声明延迟工具时，所有前端操作都实时下发。
     * </p>
     */
    private static volatile Set<String> DEFERRED_TOOLS = Collections.emptySet();

    /**
     * 设置延迟工具名集合（由 SkillCapabilityEngine 在刷新后调用）
     *
     * @param toolNames 延迟工具名集合，null 或空表示无延迟工具
     */
    public static void setDeferredTools(Set<String> toolNames) {
        DEFERRED_TOOLS = (toolNames == null || toolNames.isEmpty())
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(toolNames));
    }

    /**
     * 判断指定工具名是否为延迟工具
     */
    private static boolean isDeferredTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }
        return DEFERRED_TOOLS.contains(toolName);
    }

    /**
     * 注册 emitter 回调，用于实时下发前端操作指令
     * <p>
     * 注册后，{@link #add} 对非延迟工具调用 emitter 实时发射到 FluxSink。
     * 延迟工具存入 list，延迟到主流完成后通过 {@link #drain} 下发。
     * </p>
     *
     * @param sessionId 会话ID
     * @param emitter   发射回调
     */
    public static void registerEmitter(String sessionId, Consumer<FrontendAction> emitter) {
        if (sessionId != null && emitter != null) {
            EMITTERS.put(sessionId, emitter);
        }
    }

    /**
     * 注销 emitter（请求结束后调用，防止内存泄漏）
     *
     * @param sessionId 会话ID
     */
    public static void unregisterEmitter(String sessionId) {
        if (sessionId != null) {
            EMITTERS.remove(sessionId);
        }
    }

    /**
     * 添加一个前端操作指令到指定会话
     * <p>
     * <b>延迟工具</b>（Skill 声明在 deferredTools 中）：只存入 list（延迟通道），
     * 不走 emitter，等主流完成后 drain 下发。<br>
     * <b>其他工具</b>：通过 emitter 实时下发。若无 emitter，回退到存入 list。
     * </p>
     *
     * @param sessionId 会话ID
     * @param action    前端操作指令
     */
    public static void add(String sessionId, FrontendAction action) {
        if (sessionId == null || action == null) {
            return;
        }
        // Skill 声明的延迟工具：只存入 list，不走 emitter
        if (isDeferredTool(action.getName())) {
            HOLDER.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(action);
            return;
        }
        // 其他操作实时下发：通过 emitter 立即发射
        Consumer<FrontendAction> emitter = EMITTERS.get(sessionId);
        if (emitter != null) {
            emitter.accept(action);
        } else {
            // 无 emitter 时回退到 list（兼容未注册 emitter 的场景）
            HOLDER.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(action);
        }
    }

    /**
     * 取出指定会话累积的延迟操作指令并清理
     * <p>
     * 供主流（文本流）完成后批量下发延迟操作。
     * </p>
     *
     * @param sessionId 会话ID
     * @return 累积的延迟操作列表，无操作时返回空列表
     */
    public static List<FrontendAction> drain(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<FrontendAction> actions = HOLDER.remove(sessionId);
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(actions);
    }

    /**
     * 清理指定会话的操作指令，防止内存泄漏
     *
     * @param sessionId 会话ID
     */
    public static void clear(String sessionId) {
        if (sessionId != null) {
            HOLDER.remove(sessionId);
        }
    }

    /**
     * 检查指定会话是否有累积的延迟操作指令
     */
    public static boolean hasActions(String sessionId) {
        List<FrontendAction> actions = HOLDER.get(sessionId);
        return actions != null && !actions.isEmpty();
    }
}
