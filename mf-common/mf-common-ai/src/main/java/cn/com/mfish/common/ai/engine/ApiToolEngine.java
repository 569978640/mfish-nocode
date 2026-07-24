package cn.com.mfish.common.ai.engine;

import cn.com.mfish.common.ai.provider.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API工具引擎 —— 顶层工具注册中心
 * <p>
 * 聚合所有 {@link ToolProvider} 实现提供的工具，按微服务ID合并存储，对外提供统一查询入口。
 * 各 Assistant 通过 {@code apiToolEngine.getToolCallbackProvider(serviceId)} 获取聚合后的工具列表，
 * 无需关心工具来源（Feign / HTTP / MCP）。
 * </p>
 * <p>
 * 架构示意：
 * <pre>
 *                  ApiToolEngine
 *                       │
 *       ┌───────────────┼───────────────┐
 *       │               │               │
 *   Feign Provider  HTTP Provider   MCP Provider
 *       │               │               │
 *   Spring Cloud    OpenAPI接口       MCP Server
 *       │
 *   Controller → Service
 * </pre>
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/15
 */
@Slf4j
public class ApiToolEngine {

    /**
     * serviceId → 聚合后的工具列表
     * 多个 Provider 产生的同 serviceId 工具会合并到此列表
     */
    private final Map<String, List<ToolCallback>> toolsByService = new ConcurrentHashMap<>();

    /**
     * 注册一组由某个 Provider 提供的、属于指定服务的工具
     * <p>
     * 同一 serviceId 可被多个 Provider 注册（如 Feign 与 HTTP 同时覆盖某服务），
     * 新工具会追加到已存在的列表中。
     * </p>
     *
     * @param serviceId 微服务ID（如 mf-oauth、mf-sys）
     * @param callbacks 工具回调列表
     */
    public synchronized void register(String serviceId, List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }
        toolsByService.computeIfAbsent(serviceId, k -> new ArrayList<>()).addAll(callbacks);
        log.info("[ApiToolEngine] 注册 service={} 工具数={} 累计={}",
                serviceId, callbacks.size(), toolsByService.get(serviceId).size());
    }

    /**
     * 替换指定服务的全部工具（先清空再注册）
     * <p>
     * 用于 HttpToolProvider 异步重试成功后，动态更新某服务的工具列表。
     * 与 {@link #register} 的区别：register 是追加，replace 是覆盖。
     * </p>
     *
     * @param serviceId 微服务ID
     * @param callbacks 新的工具回调列表
     */
    public synchronized void replace(String serviceId, List<ToolCallback> callbacks) {
        if (serviceId == null || serviceId.isEmpty()) return;
        int oldSize = toolsByService.getOrDefault(serviceId, List.of()).size();
        if (callbacks == null || callbacks.isEmpty()) {
            toolsByService.remove(serviceId);
        } else {
            toolsByService.put(serviceId, new ArrayList<>(callbacks));
        }
        log.info("[ApiToolEngine] 替换 service={} 工具数: {} → {}",
                serviceId, oldSize, callbacks != null ? callbacks.size() : 0);
    }

    /**
     * 初始化：遍历所有 ToolProvider，聚合其发现的工具
     * <p>
     * 由 {@code ApiToolAutoConfiguration} 在启动期调用。每个 Provider 独立发现工具，
     * Engine 按 serviceId 合并，实现"来源透明"。
     * </p>
     *
     * @param providers 所有已注册的 ToolProvider 实现
     */
    public void initialize(List<ToolProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            log.warn("[ApiToolEngine] 未发现任何 ToolProvider，工具集为空");
            return;
        }
        int totalTools = 0;
        for (ToolProvider provider : providers) {
            try {
                Map<String, List<ToolCallback>> tools = provider.discoverTools();
                if (tools == null || tools.isEmpty()) {
                    log.info("[ApiToolEngine] Provider={} 未发现工具", provider.getType());
                    continue;
                }
                for (Map.Entry<String, List<ToolCallback>> entry : tools.entrySet()) {
                    register(entry.getKey(), entry.getValue());
                    totalTools += entry.getValue().size();
                }
                log.info("[ApiToolEngine] Provider={} 注册完成，服务数={}",
                        provider.getType(), tools.size());
            } catch (Exception e) {
                log.error("[ApiToolEngine] Provider={} 发现工具失败", provider.getType(), e);
            }
        }
        log.info("[ApiToolEngine] 初始化完成，共注册 {} 个服务，{} 个工具",
                toolsByService.size(), totalTools);
    }

    /**
     * 获取指定服务的全部工具（跨所有 Provider 聚合）
     *
     * @param serviceId 微服务ID
     * @return 工具回调列表，无匹配时返回空列表
     */
    private List<ToolCallback> getToolCallbacks(String serviceId) {
        return toolsByService.getOrDefault(serviceId, List.of());
    }

    /**
     * 聚合多个微服务的全部工具
     * <p>
     * 用于自主规划+多轮工具调用场景：Executor 按计划步骤指定的服务集合，
     * 一次性聚合这些服务的全部工具供 LLM 选择。
     * 同名工具会按 serviceId 顺序去重（先注册的保留）。
     * </p>
     *
     * @param serviceIds 微服务ID集合
     * @return 聚合后的工具列表
     */
    private List<ToolCallback> getToolCallbacks(Collection<String> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        // 用 LinkedHashMap 按 tool name 去重，保留首次出现的版本
        Map<String, ToolCallback> dedup = new LinkedHashMap<>();
        for (String sid : serviceIds) {
            for (ToolCallback tc : getToolCallbacks(sid)) {
                String name = tc.getToolDefinition().name();
                dedup.putIfAbsent(name, tc);
            }
        }
        return new ArrayList<>(dedup.values());
    }

    /**
     * 获取指定服务的 ToolCallbackProvider（Spring AI 2.0 推荐方式）
     * <p>
     * 通过 {@code .tools(apiToolEngine.getToolCallbackProvider(serviceId))} 传入 ChatClient。
     * </p>
     *
     * @param serviceId 微服务ID
     * @return ToolCallbackProvider，无匹配时返回空 Provider
     */
    public ToolCallbackProvider getToolCallbackProvider(String serviceId) {
        return ToolCallbackProvider.from(getToolCallbacks(serviceId));
    }

    /**
     * 聚合多个微服务的工具为 ToolCallbackProvider
     * <p>
     * 用于自主规划场景：Executor 按计划步骤指定的服务集合聚合工具，
     * 同名工具按 serviceId 顺序去重。
     * </p>
     *
     * @param serviceIds 微服务ID集合
     * @return ToolCallbackProvider，无匹配时返回空 Provider
     */
    public ToolCallbackProvider getToolCallbackProvider(Set<String> serviceIds) {
        return ToolCallbackProvider.from(getToolCallbacks(serviceIds));
    }

    /**
     * 获取所有已注册的微服务ID
     * <p>
     * 供 {@code ToolCapabilityEngine} 枚举所有服务的工具，将其暴露为 ActionDefinition。
     * </p>
     *
     * @return 已注册的 serviceId 集合（不可变快照）
     */
    public Set<String> getAllServiceIds() {
        return Set.copyOf(toolsByService.keySet());
    }

    /**
     * 获取所有以指定前缀开头的 serviceId（用于聚合 MCP / Skill 扩展工具）
     * <p>
     * MCP 工具 serviceId = MCP 服务器名（通常以 mcp- 开头，如 mcp-everything）；
     * Skill 工具 serviceId = skill-{skillCode}（如 skill-translator）。
     * 垂直助手通过本方法聚合扩展工具，实现跨域调用 MCP/Skill 能力。
     * </p>
     *
     * @param prefix serviceId 前缀（如 "mcp-"、"skill-"）
     * @return 匹配前缀的 serviceId 集合（不可变快照）
     */
    public Set<String> getServiceIdsByPrefix(String prefix) {
        return toolsByService.keySet().stream()
                .filter(id -> id != null && id.startsWith(prefix))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 获取扩展工具的 serviceId 集合（排除指定的微服务ID）
     * <p>
     * 扩展工具包括 MCP 工具（serviceId=MCP服务器名）和 Skill 工具（serviceId=skill-{code}）。
     * 它们的 serviceId 不属于标准微服务命名空间（mf-*），通过排除微服务ID即可获取。
     * </p>
     * <p>
     * 垂直助手通过本方法聚合扩展工具，实现"主服务工具 + MCP/Skill 扩展工具"的组合调用。
     * </p>
     *
     * @param microServiceIds 标准微服务ID集合（如 MfService.allServiceIds()），这些会被排除
     * @return 扩展工具的 serviceId 集合（不可变快照）
     */
    public Set<String> getExtendedServiceIds(Set<String> microServiceIds) {
        return toolsByService.keySet().stream()
                .filter(id -> id != null && !microServiceIds.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 获取所有服务的全部工具（跨服务，同名工具按 serviceId 顺序去重）
     * <p>
     * 供 {@code ToolCapabilityEngine} 构建全局动作列表。
     * 去重策略与 {@link #getToolCallbacks(Collection)} 一致：先注册的保留。
     * </p>
     *
     * @return 全部工具列表（去重后的快照）
     */
    public List<ToolCallback> getAllToolCallbacks() {
        return getToolCallbacks(toolsByService.keySet());
    }

    /**
     * 按动作名查找对应的 ToolCallback
     * <p>
     * 供 {@code ToolCapabilityEngine.execute()} 通过 actionName 路由到具体工具。
     * 遍历所有服务，返回首个名称匹配的 ToolCallback。
     * </p>
     *
     * @param actionName 动作名（即 ToolCallback.getToolDefinition().name()）
     * @return 匹配的 ToolCallback，未找到时返回 null
     */
    public ToolCallback findToolCallback(String actionName) {
        if (actionName == null || actionName.isEmpty()) {
            return null;
        }
        for (List<ToolCallback> callbacks : toolsByService.values()) {
            for (ToolCallback tc : callbacks) {
                if (actionName.equals(tc.getToolDefinition().name())) {
                    return tc;
                }
            }
        }
        return null;
    }
}
