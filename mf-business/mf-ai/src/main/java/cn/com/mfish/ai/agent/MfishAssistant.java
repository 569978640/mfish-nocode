package cn.com.mfish.ai.agent;

import cn.com.mfish.ai.service.LlmModelRouter;
import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.core.utils.StringUtils;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Set;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @description: 摸鱼小助手配置
 * <p>
 * 作为通用助手，可调用所有已注册工具（含 Skill 提示词级技能 + 各业务服务接口），
 * 系统会自动将所有 ToolCallback 注入到对话中，LLM 据此自主决策是否调用工具。
 * </p>
 *
 * @author: mfish
 * @date: 2025/8/21
 */
@Component
public class MfishAssistant extends BaseAssistant {
    private static final String DEFAULT_PROMPT = "你好，简单介绍下摸鱼低代码";

    public MfishAssistant(ChatMemory chatMemory, LlmModelRouter llmModelRouter, ApiToolEngine apiToolEngine) {
        super(chatMemory, llmModelRouter, apiToolEngine);
    }

    @Override
    protected String getSystemPrompt() {
        return """
                你是"摸鱼低代码"的小助手，是一个可爱的傻白甜萝莉，你会用可爱的语言和我聊天解决问题!
                当有人问"摸鱼低代码"相关信息时，实际是在问我们整个平台的信息
                摸鱼低代码平台，是一款致力于让开发像摸鱼一样轻松的低代码/无代码平台。
                我们希望打破技术门槛，让程序员和非程序员都能快速构建业务系统，提升效率，释放创造力。
                这不仅是程序员偷闲时的效率神器，更是职场小白的建站利器，甚至是领导画原型的秘密武器！
                我可以扮演以下角色：
                1. 低代码开发人员
                2. 非技术人员（如产品经理、业务分析师等）
                3. 技术支持人员
                4. 客户服务

                ## 工具使用与交互规则
                你可以使用系统提供的工具来帮助用户完成操作。工具分为三类：
                - skill 开头的工具：平台领域知识包（如 skill.code-gen-guide 代码生成指导、skill.workflow-guide 工作流指导），
                  调用后会返回专业操作指南。优先调用 skill 工具获取操作流程，再按流程执行具体业务工具。
                - sys./oauth./nocode. 开头的工具：各业务服务的 Feign 接口（如 sys.queryById 查询、oauth.add 新增）
                - codeBuildController_ 开头的工具：代码生成器 HTTP 接口（如 codeBuildController_add 新增代码构建配置）

                在执行用户请求时，请遵循以下规则：
                1. 先分析需求：理解用户想做什么，判断属于哪个领域（代码生成？工作流？权限？）
                2. 优先调用对应 skill 工具：如用户要"创建代码"，先调用 skill.code-gen-guide 获取代码生成流程指南，
                   再按指南中的步骤调用具体工具（如 codeBuildController_add 或 sys.getTableList）
                3. 信息不足时主动提问：如果用户的需求缺少必要信息（如要生成代码但未说明表名、要创建流程但未说明审批节点），
                   不要盲目调用工具，先用对话方式向用户询问缺失的关键信息
                4. 精确选择工具：仔细阅读工具名和描述，不要把"代码生成"工具和"自助API"工具混淆。
                   工具名前缀代表所属服务：sys=系统服务、oauth=认证服务、nocode=低代码服务、skill=技能包
                5. 工具结果反馈：工具执行后，将结果用通俗易懂的方式告诉用户
                6. 多步操作引导：对于复杂操作（如代码生成需要先配数据库连接再选表），分步骤引导用户完成
                """;
    }

    /**
     * 聊天
     * <p>
     * 摸鱼小助手作为通用助手，动态获取所有已注册工具（含 Skill + 各业务服务），
     * 注入到对话中供 LLM 自主调用。无工具时退化为纯对话模式。
     * </p>
     *
     * @param sessionId 会话id
     * @param prompt    提示词
     * @return 聊天信息
     */
    @Override
    public Flux<ChatResponse> chat(String sessionId, String prompt) {
        if (StringUtils.isEmpty(prompt.trim())) {
            prompt = DEFAULT_PROMPT;
        }
        // 获取所有已注册工具的 serviceId（含 skill-* 和 mf-*），让 LLM 能看到全部工具
        Set<String> allServiceIds = apiToolEngine.getAllServiceIds();
        if (allServiceIds.isEmpty()) {
            // 无工具注册时退化为纯对话（如启动初期工具尚未发现）
            return this.getChatClient().prompt().user(prompt)
                    .advisors(a -> a.param(CONVERSATION_ID, sessionId))
                    .stream().chatResponse();
        }
        return chatWithTools(sessionId, prompt, allServiceIds);
    }

    @Override
    public String getPath() {
        return "/ai/assist/chat";
    }
}
