package cn.com.mfish.common.ai.capability;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力引擎门面（Capability Engine）
 * <p>
 * 组合（Composite）所有 {@link CapabilitySubEngine} 实例，向上为 Planner 提供统一的能力发现与执行入口。
 * 屏蔽异构执行细节：Planner 无需关心动作来自 TOOL / SKILL / MCP / WORKFLOW 哪个引擎。
 * </p>
 * <p>
 * <b>架构定位</b>：
 * <pre>
 *                  CapabilityEngine（门面）
 *                       │
 *       ┌───────────────┼───────────────┬───────────────┬───────────────┐
 *       │               │               │               │
 *   ToolCapability  SkillCapability  McpCapability  WorkflowCapability
 *   (Engine)         (Engine)         (Engine)         (Engine)
 *       │               │               │               │
 *   ApiToolEngine    Java Skill       MCP Client      Flowable/BPMN
 *   (Feign+HTTP)
 * </pre>
 * </p>
 * <p>
 * <b>与现有 ApiToolEngine 的关系</b>：
 * <ul>
 *     <li>ApiToolEngine 保持不变，仍由 {@code BaseAssistant.chatWithTools()} 直接调用，
 *         支撑 LLM 驱动模式（把工具交给 ChatClient）</li>
 *     <li>CapabilityEngine 是上层抽象，ToolCapabilityEngine 适配 ApiToolEngine 暴露其动作元数据</li>
 *     <li>两条链路并存：LLM 驱动走 ChatClient.tools()，显式调用走 CapabilityEngine.executeAction()</li>
 * </ul>
 * </p>
 * <p>
 * <b>线程安全</b>：子引擎注册在初始化期完成，运行期只读；动作查找通过 ConcurrentHashMap 支持并发。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Slf4j
public class CapabilityEngine {

    /**
     * 已注册的子引擎列表（按注册顺序）
     */
    private final List<CapabilitySubEngine> subEngines = Collections.synchronizedList(new ArrayList<>());

    /**
     * 动作名 → 子引擎 的索引（供 executeAction 快速路由）
     * <p>
     * 初始化时构建，子引擎动态新增动作时需调用 {@link #refreshActionIndex()} 重建。
     * 同名动作按子引擎注册顺序，先注册的覆盖（与 ApiToolEngine 去重策略一致）。
     * </p>
     */
    private volatile Map<String, CapabilitySubEngine> actionIndex = new ConcurrentHashMap<>();

    /**
     * 注册子引擎
     * <p>
     * 在 {@link CapabilityAutoConfiguration} 初始化期调用，运行期不应动态增减。
     * 注册后自动重建动作索引。
     * </p>
     *
     * @param subEngine 子引擎实例
     */
    public synchronized void registerSubEngine(CapabilitySubEngine subEngine) {
        if (subEngine == null) {
            return;
        }
        subEngines.add(subEngine);
        log.info("[CapabilityEngine] 注册子引擎 type={} actions={}",
                subEngine.getEngineType(),
                subEngine.getActions() != null ? subEngine.getActions().size() : 0);
        rebuildActionIndex();
    }

    /**
     * 批量注册子引擎
     *
     * @param engines 子引擎列表
     */
    public synchronized void registerSubEngines(List<CapabilitySubEngine> engines) {
        if (engines == null || engines.isEmpty()) {
            return;
        }
        for (CapabilitySubEngine engine : engines) {
            if (engine != null) {
                subEngines.add(engine);
            }
        }
        log.info("[CapabilityEngine] 批量注册 {} 个子引擎", subEngines.size());
        rebuildActionIndex();
    }

    /**
     * 获取所有子引擎的可用动作（并集）
     * <p>
     * Planner 调用此方法获取全局动作列表，可选择：
     * <ul>
     *     <li>将动作列表注入系统提示词，供 LLM 决策</li>
     *     <li>将动作列表转换为 ToolCallback 传给 ChatClient（LLM 驱动模式）</li>
     *     <li>根据动作元数据自行决策后调用 {@link #executeAction}（显式调用模式）</li>
     * </ul>
     * </p>
     *
     * @return 所有子引擎动作的并集，无子引擎时返回空列表
     */
    public List<ActionDefinition> getAvailableActions() {
        List<ActionDefinition> all = new ArrayList<>();
        for (CapabilitySubEngine engine : subEngines) {
            try {
                List<ActionDefinition> actions = engine.getActions();
                if (actions != null) {
                    all.addAll(actions);
                }
            } catch (Exception e) {
                log.error("[CapabilityEngine] 子引擎 type={} 获取动作列表失败",
                        engine.getEngineType(), e);
            }
        }
        return all;
    }

    /**
     * 按引擎类型过滤可用动作
     *
     * @param engineType 引擎类型
     * @return 该类型引擎的动作列表
     */
    public List<ActionDefinition> getAvailableActions(EngineType engineType) {
        List<ActionDefinition> filtered = new ArrayList<>();
        for (CapabilitySubEngine engine : subEngines) {
            if (engine.getEngineType() == engineType) {
                try {
                    List<ActionDefinition> actions = engine.getActions();
                    if (actions != null) {
                        filtered.addAll(actions);
                    }
                } catch (Exception e) {
                    log.error("[CapabilityEngine] 子引擎 type={} 获取动作列表失败", engineType, e);
                }
            }
        }
        return filtered;
    }

    /**
     * 执行动作（统一入口）
     * <p>
     * 按 actionName 路由到对应子引擎执行。Planner 显式调用模式走此方法。
     * </p>
     *
     * @param actionName 动作名称（需与 ActionDefinition.name 一致）
     * @param params     动作参数
     * @param ctx        执行上下文
     * @return 执行结果；动作不存在时返回 failure
     */
    public ActionResult executeAction(String actionName, Map<String, Object> params, ExecutionContext ctx) {
        if (actionName == null || actionName.isEmpty()) {
            return ActionResult.failure(EngineType.TOOL, "actionName 不能为空", 0);
        }
        CapabilitySubEngine engine = actionIndex.get(actionName);
        if (engine == null) {
            return ActionResult.failure(EngineType.TOOL,
                    "未找到动作: " + actionName + "，可用动作: " + actionIndex.keySet(), 0);
        }
        long start = System.currentTimeMillis();
        try {
            ActionResult result = engine.execute(actionName, params, ctx);
            long elapsed = System.currentTimeMillis() - start;
            // 子引擎可能未填充耗时，此处兜底
            if (result.getDurationMs() <= 0) {
                result.setDurationMs(elapsed);
            }
            log.info("[CapabilityEngine] 执行动作 name={} engine={} success={} cost={}ms",
                    actionName, engine.getEngineType(), result.isSuccess(), elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[CapabilityEngine] 执行动作异常 name={} engine={}",
                    actionName, engine.getEngineType(), e);
            return ActionResult.failure(engine.getEngineType(),
                    "执行异常: " + e.getMessage(), elapsed);
        }
    }

    /**
     * 重建动作索引（子引擎动态新增动作后调用）
     */
    public synchronized void refreshActionIndex() {
        rebuildActionIndex();
    }

    /**
     * 获取已注册的子引擎类型列表（供调试和监控）
     */
    public List<EngineType> getRegisteredEngineTypes() {
        List<EngineType> types = new ArrayList<>();
        for (CapabilitySubEngine engine : subEngines) {
            types.add(engine.getEngineType());
        }
        return types;
    }

    /**
     * 获取已注册的动作总数（供调试和监控）
     */
    public int getActionCount() {
        return actionIndex.size();
    }

    /**
     * 重建动作索引：遍历所有子引擎，收集动作名 → 子引擎映射
     * <p>
     * 同名动作按子引擎注册顺序，先注册的保留（与 ApiToolEngine 去重策略一致）。
     * </p>
     */
    private void rebuildActionIndex() {
        Map<String, CapabilitySubEngine> newIndex = new LinkedHashMap<>();
        for (CapabilitySubEngine engine : subEngines) {
            try {
                List<ActionDefinition> actions = engine.getActions();
                if (actions == null) {
                    continue;
                }
                for (ActionDefinition action : actions) {
                    if (action == null || action.getName() == null) {
                        continue;
                    }
                    // 先注册的保留，后注册的跳过（避免同名冲突）
                    newIndex.putIfAbsent(action.getName(), engine);
                }
            } catch (Exception e) {
                log.error("[CapabilityEngine] 重建索引时子引擎 type={} 获取动作失败",
                        engine.getEngineType(), e);
            }
        }
        this.actionIndex = new ConcurrentHashMap<>(newIndex);
        log.info("[CapabilityEngine] 动作索引重建完成，共 {} 个动作", actionIndex.size());
    }
}
