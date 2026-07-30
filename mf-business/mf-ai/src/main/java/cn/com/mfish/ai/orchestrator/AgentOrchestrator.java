package cn.com.mfish.ai.orchestrator;

import cn.com.mfish.ai.agent.Executor;
import cn.com.mfish.ai.agent.Planner;
import cn.com.mfish.ai.runtime.ToolRuntime;
import cn.com.mfish.ai.service.FileParseService;
import cn.com.mfish.common.ai.agent.EventBus;
import cn.com.mfish.common.ai.agent.TenantContext;
import cn.com.mfish.common.ai.entity.AgentPlan;
import cn.com.mfish.common.ai.entity.AiRequest;
import cn.com.mfish.common.ai.entity.ChatResponseVo;
import cn.com.mfish.common.ai.entity.EventType;
import cn.com.mfish.common.ai.entity.PlanStep;
import cn.com.mfish.common.ai.memory.ConversationMemory;
import cn.com.mfish.common.ai.memory.ConversationMemoryStore;
import cn.com.mfish.common.ai.memory.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Agent orchestration boundary.
 *
 * <p>This component owns the Plan -> Execute -> Emit lifecycle. Controllers and
 * compatibility facades should call this rather than wiring Planner and Executor
 * directly.</p>
 */
@Slf4j
@Component
public class AgentOrchestrator {

    private final Planner planner;
    private final Executor executor;
    private final FileParseService fileParseService;
    private final ConversationMemoryStore memoryStore;
    private final ToolRuntime toolRuntime;

    public AgentOrchestrator(Planner planner, Executor executor, FileParseService fileParseService,
                             ConversationMemoryStore memoryStore, ToolRuntime toolRuntime) {
        this.planner = planner;
        this.executor = executor;
        this.fileParseService = fileParseService;
        this.memoryStore = memoryStore;
        this.toolRuntime = toolRuntime;
    }

    public Flux<ChatResponseVo> run(AiRequest aiRequest) {
        String requestId = aiRequest.getId();
        String sessionId = aiRequest.getSessionId();
        String prompt = aiRequest.getMessage() != null ? aiRequest.getMessage().getContent() : null;
        EventBus eventBus = new EventBus(requestId);

        ConversationMemory memory = memoryStore.getOrCreate(sessionId);
        TenantContext tenantContext = toolRuntime.captureTenantContext();
        memory.bindTenantContext(tenantContext);

        List<DocumentChunk> chunks = fileParseService.loadAsChunks(aiRequest.getFileIds());
        if (!chunks.isEmpty()) {
            memory.addDocumentChunks(chunks);
        }

        runOrchestration(sessionId, prompt, eventBus, tenantContext);
        return eventBus.asFlux();
    }

    private void runOrchestration(String sessionId, String prompt, EventBus eventBus, TenantContext tenantContext) {
        planner.plan(sessionId, prompt, tenantContext)
                .doOnNext(plan -> {
                    log.info("[AgentOrchestrator] plan created, steps={}",
                            plan.getSteps() != null ? plan.getSteps().size() : 0);
                    eventBus.emit(EventType.PLAN_CREATED, formatPlanSummary(plan));
                })
                .flatMap(plan -> executeAllSteps(plan, sessionId, eventBus, tenantContext))
                .doOnSuccess(finalResult -> {
                    log.info("[AgentOrchestrator] orchestration completed");
                    eventBus.emit(ChatResponseVo.ofEvent(EventType.PLAN_COMPLETED, finalResult)
                            .setFinishReason("STOP"));
                    eventBus.complete();
                })
                .doOnError(e -> {
                    log.error("[AgentOrchestrator] orchestration failed", e);
                    eventBus.emitError("编排失败: " + e.getMessage());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<String> executeAllSteps(AgentPlan plan, String sessionId, EventBus eventBus,
                                         TenantContext tenantContext) {
        List<PlanStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            return Mono.just("无可执行步骤");
        }

        Mono<String> chain = Mono.just("");
        for (int i = 0; i < steps.size(); i++) {
            final int index = i;
            final PlanStep step = steps.get(i);
            chain = chain.flatMap(prevResult ->
                    executor.execute(step, index, sessionId, plan, eventBus, tenantContext)
            );
        }

        return chain.map(lastResult -> {
            StringBuilder summary = new StringBuilder();
            summary.append("共执行").append(steps.size()).append("个步骤：\n\n");
            for (int i = 0; i < steps.size(); i++) {
                PlanStep step = steps.get(i);
                summary.append("步骤").append(i + 1).append("：").append(step.getDescription());
                if ("COMPLETED".equals(step.getStatus())) {
                    summary.append(" [完成]");
                } else if ("FAILED".equals(step.getStatus())) {
                    summary.append(" [失败]");
                }
                summary.append("\n");
            }
            summary.append("\n").append(lastResult);
            return summary.toString();
        });
    }

    private String formatPlanSummary(AgentPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.getSummary() != null ? plan.getSummary() : "").append("\n");
        if (plan.getSteps() != null) {
            for (int i = 0; i < plan.getSteps().size(); i++) {
                PlanStep step = plan.getSteps().get(i);
                sb.append(i + 1).append(". ").append(step.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }
}
