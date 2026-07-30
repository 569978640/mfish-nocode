package cn.com.mfish.ai.runtime;

import cn.com.mfish.ai.service.LlmModelRouter;
import cn.com.mfish.common.ai.agent.TenantContext;
import cn.com.mfish.common.ai.capability.ActionDefinition;
import cn.com.mfish.common.ai.capability.CapabilityEngine;
import cn.com.mfish.common.ai.capability.EngineType;
import cn.com.mfish.common.ai.capability.SkillCapabilityEngine;
import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.ai.entity.AgentPlan;
import cn.com.mfish.common.ai.entity.FrontendAction;
import cn.com.mfish.common.ai.entity.PlanStep;
import cn.com.mfish.common.ai.frontend.FrontendActionHolder;
import cn.com.mfish.common.ai.frontend.FrontendActionTool;
import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.constants.ServiceConstants;
import cn.com.mfish.common.core.utils.AuthInfoUtils;
import cn.com.mfish.common.core.utils.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Runtime boundary for tools.
 *
 * <p>Assistants, Planner and Executor should ask this component for tool-facing
 * decisions instead of directly coupling to ApiToolEngine, Skill internals or
 * FrontendActionHolder.</p>
 */
@Slf4j
@Component
public class ToolRuntime {

    private final LlmModelRouter llmModelRouter;
    private final ApiToolEngine apiToolEngine;
    private final CapabilityEngine capabilityEngine;
    private final ObjectProvider<SkillCapabilityEngine> skillCapabilityEngineProvider;

    public ToolRuntime(LlmModelRouter llmModelRouter,
                       ApiToolEngine apiToolEngine,
                       CapabilityEngine capabilityEngine,
                       ObjectProvider<SkillCapabilityEngine> skillCapabilityEngineProvider) {
        this.llmModelRouter = llmModelRouter;
        this.apiToolEngine = apiToolEngine;
        this.capabilityEngine = capabilityEngine;
        this.skillCapabilityEngineProvider = skillCapabilityEngineProvider;
    }

    public Set<String> getAllServiceIds() {
        return apiToolEngine.getAllServiceIds();
    }

    public ToolCallbackProvider getToolCallbackProvider(Set<String> serviceIds) {
        return apiToolEngine.getToolCallbackProvider(serviceIds);
    }

    public Set<String> resolveAssistantServiceIds(String mainServiceId) {
        Set<String> serviceIds = new HashSet<>();
        serviceIds.add(mainServiceId);
        serviceIds.addAll(apiToolEngine.getExtendedServiceIds(ServiceConstants.MfService.allServiceIds()));
        mergeGuideRequiresInto(serviceIds);
        return serviceIds;
    }

    public Set<String> resolveStepServiceIds(Collection<String> serviceIds) {
        Set<String> resolved = serviceIds != null ? new HashSet<>(serviceIds) : new HashSet<>();
        resolved.add(FrontendActionTool.FRONTEND_SERVICE_ID);
        return resolved;
    }

    public void enrichPlanServiceIds(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return;
        }
        Set<String> allSkillServiceIds = collectAllSkillServiceIds();
        SkillCapabilityEngine skillEngine = skillCapabilityEngineProvider.getIfAvailable();
        for (PlanStep step : plan.getSteps()) {
            Set<String> merged = step.getServiceIds() != null
                    ? new LinkedHashSet<>(step.getServiceIds())
                    : new LinkedHashSet<>();
            merged.addAll(allSkillServiceIds);
            if (skillEngine != null) {
                for (String serviceId : allSkillServiceIds) {
                    String skillCode = serviceId.substring("skill-".length());
                    List<String> requires = skillEngine.getGuideRequires("skill." + skillCode);
                    if (requires != null && !requires.isEmpty()) {
                        merged.addAll(requires);
                        log.info("[ToolRuntime] merge guide requires skill={} requires={} serviceIds={}",
                                skillCode, requires, merged);
                    }
                }
            }
            step.setServiceIds(new ArrayList<>(merged));
        }
    }

    public TenantContext captureTenantContext() {
        String tenantId = llmModelRouter.currentTenantId();
        String userId = AuthInfoUtils.getCurrentUserId();
        String accessToken = AuthInfoUtils.getAccessToken();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServerWebExchange serverWebExchange = ServletUtils.getExchange();
        return new TenantContext(tenantId, userId, accessToken, requestAttributes, serverWebExchange);
    }

    public Map<String, Object> buildToolContext(String sessionId, TenantContext tenantContext) {
        String tenantId;
        String userId;
        String accessToken;
        Object requestAttributes;
        Object serverWebExchange;
        if (tenantContext != null) {
            tenantId = tenantContext.getTenantId();
            userId = tenantContext.getUserId();
            accessToken = tenantContext.getAccessToken();
            requestAttributes = tenantContext.getRequestAttributes();
            serverWebExchange = tenantContext.getServerWebExchange();
        } else {
            tenantId = AuthInfoUtils.getCurrentTenantId();
            userId = AuthInfoUtils.getCurrentUserId();
            accessToken = AuthInfoUtils.getAccessToken();
            requestAttributes = RequestContextHolder.getRequestAttributes();
            serverWebExchange = ServletUtils.getExchange();
        }

        Map<String, Object> context = new HashMap<>();
        context.put(RPCConstants.REQ_USER_ID, userId != null ? userId : "");
        context.put(RPCConstants.REQ_TENANT_ID, tenantId != null ? tenantId : AuthInfoUtils.SUPER_TENANT_ID);
        context.put(RPCConstants.REQ_ORIGIN, RPCConstants.AI);
        if (accessToken != null && !accessToken.isEmpty()) {
            context.put(RPCConstants.REQ_TOKEN, accessToken);
        }
        if (requestAttributes instanceof RequestAttributes ra) {
            context.put(RPCConstants.REQ_REQUEST_ATTRIBUTES, ra);
        }
        if (serverWebExchange instanceof ServerWebExchange swe) {
            context.put(RPCConstants.REQ_SERVER_WEB_EXCHANGE, swe);
        }
        context.put(FrontendActionHolder.CTX_SESSION_ID, sessionId);
        return context;
    }

    public String buildToolUsageHint(ToolCallbackProvider toolProvider) {
        ToolCallback[] callbacks = toolProvider.getToolCallbacks();
        if (callbacks.length == 0) {
            return "## 工具使用规则\n当前没有可用工具，请直接根据你的知识回答用户问题。";
        }
        StringBuilder sb = new StringBuilder("## 工具使用规则\n");
        sb.append("你只能使用以下工具，不能虚构任何工具名：\n");
        boolean hasSkill = false;
        boolean hasFrontend = false;
        for (ToolCallback callback : callbacks) {
            String name = callback.getToolDefinition().name();
            String desc = callback.getToolDefinition().description();
            if (name.startsWith("skill.")) {
                hasSkill = true;
            }
            if (name.startsWith("frontend.")) {
                hasFrontend = true;
            }
            sb.append("- ").append(name);
            if (desc != null && !desc.isEmpty()) {
                sb.append(": ").append(desc);
            }
            sb.append("\n");
        }
        sb.append("\n调用规则：\n");
        sb.append("1. 先分析用户意图，从工具列表中选择最合适的工具。\n");
        sb.append("2. 只能使用上方列出的工具名，不要构造新工具名。\n");
        sb.append("3. 没有合适工具时，直接回答用户。\n");
        sb.append("4. 工具调用失败时，根据错误信息调整参数或选择其他工具。\n");
        if (hasSkill && hasFrontend) {
            sb.append("5. 如果调用 skill.* 获取到操作指南，必须继续按指南调用业务工具和 frontend.* 工具，不要只返回指南内容。\n");
        }
        return sb.toString();
    }

    public void resetFrontendActions(String sessionId) {
        FrontendActionHolder.clear(sessionId);
        FrontendActionHolder.unregisterEmitter(sessionId);
    }

    public void registerFrontendEmitter(String sessionId, Consumer<FrontendAction> emitter) {
        FrontendActionHolder.registerEmitter(sessionId, emitter);
    }

    public void unregisterFrontendEmitter(String sessionId) {
        FrontendActionHolder.unregisterEmitter(sessionId);
    }

    public void clearFrontendActions(String sessionId) {
        FrontendActionHolder.clear(sessionId);
    }

    public List<FrontendAction> drainFrontendActions(String sessionId) {
        return FrontendActionHolder.drain(sessionId);
    }

    private void mergeGuideRequiresInto(Set<String> serviceIds) {
        SkillCapabilityEngine skillEngine = skillCapabilityEngineProvider.getIfAvailable();
        if (skillEngine == null || serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        Set<String> skillServiceIds = new HashSet<>();
        for (String serviceId : serviceIds) {
            if (serviceId != null && serviceId.startsWith("skill-")) {
                skillServiceIds.add(serviceId);
            }
        }
        for (String skillServiceId : skillServiceIds) {
            String skillCode = skillServiceId.substring("skill-".length());
            List<String> requires = skillEngine.getGuideRequires("skill." + skillCode);
            if (requires != null && !requires.isEmpty()) {
                serviceIds.addAll(requires);
                log.info("[ToolRuntime] merge assistant guide requires skill={} requires={} serviceIds={}",
                        skillCode, requires, serviceIds);
            }
        }
    }

    private Set<String> collectAllSkillServiceIds() {
        Set<String> skillServiceIds = new LinkedHashSet<>();
        try {
            List<ActionDefinition> actions = capabilityEngine.getAvailableActions(EngineType.SKILL);
            if (actions != null) {
                for (ActionDefinition action : actions) {
                    if (action.getServiceId() != null && action.getServiceId().startsWith("skill-")) {
                        skillServiceIds.add(action.getServiceId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ToolRuntime] collect skill serviceIds failed", e);
        }
        return skillServiceIds;
    }
}
