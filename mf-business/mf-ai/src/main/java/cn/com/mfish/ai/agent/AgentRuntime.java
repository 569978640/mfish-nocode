package cn.com.mfish.ai.agent;

import cn.com.mfish.ai.orchestrator.AgentOrchestrator;
import cn.com.mfish.common.ai.entity.AiRequest;
import cn.com.mfish.common.ai.entity.ChatResponseVo;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Compatibility facade for existing controller wiring.
 *
 * <p>New orchestration code belongs to {@link AgentOrchestrator}.</p>
 */
@Component
public class AgentRuntime {

    private final AgentOrchestrator agentOrchestrator;

    public AgentRuntime(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    public Flux<ChatResponseVo> run(AiRequest aiRequest) {
        return agentOrchestrator.run(aiRequest);
    }
}
