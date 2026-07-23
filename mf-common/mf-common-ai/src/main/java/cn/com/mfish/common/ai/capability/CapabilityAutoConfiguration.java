package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.engine.ApiToolEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 能力引擎自动配置
 * <p>
 * 注册 {@link CapabilityEngine} 作为能力引擎门面，自动发现所有 {@link CapabilitySubEngine} 实现，
 * 在所有单例 Bean 初始化完成后将子引擎注册到 CapabilityEngine。
 * </p>
 * <p>
 * 默认注册两个子引擎：
 * <ul>
 *     <li>{@link ToolCapabilityEngine} — 适配 ApiToolEngine（Feign/OpenAPI 工具）</li>
 *     <li>{@link McpCapabilityEngine} — MCP 协议工具（条件注册，需 MCP SDK + McpServerConfigProvider）</li>
 * </ul>
 * 其他子引擎（SkillCapabilityEngine / WorkflowCapabilityEngine）
 * 通过实现 CapabilitySubEngine 接口并声明为 Bean 即可自动接入。
 * </p>
 * <p>
 * <b>与 ApiToolAutoConfiguration 的关系</b>：
 * <ul>
 *     <li>ApiToolAutoConfiguration 负责 ApiToolEngine 的初始化（工具发现与聚合）</li>
 *     <li>CapabilityAutoConfiguration 负责 CapabilityEngine 的初始化（子引擎注册与动作索引构建）</li>
 *     <li>两者通过 ApiToolEngine Bean 关联：ToolCapabilityEngine 依赖 ApiToolEngine</li>
 *     <li>初始化顺序由 Spring 容器保证：ApiToolEngine 的 SmartInitializingSingleton 先执行，
 *         CapabilityEngine 的 SmartInitializingSingleton 后执行</li>
 * </ul>
 * </p>
 * <p>
 * 通过 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 注册。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Slf4j
@AutoConfiguration
public class CapabilityAutoConfiguration {

    /**
     * 能力引擎门面
     */
    @Bean
    @ConditionalOnMissingBean
    public CapabilityEngine capabilityEngine() {
        return new CapabilityEngine();
    }

    /**
     * 工具能力引擎适配器（包装 ApiToolEngine 为 CapabilitySubEngine）
     * <p>
     * 依赖 ApiToolEngine Bean，由 ApiToolAutoConfiguration 注册。
     * </p>
     */
    @Bean
    public ToolCapabilityEngine toolCapabilityEngine(ApiToolEngine apiToolEngine) {
        return new ToolCapabilityEngine(apiToolEngine);
    }

    /**
     * MCP 能力引擎（条件注册）
     * <p>
     * 仅当 classpath 中存在 MCP SDK 类 且 容器中有 {@link McpServerConfigProvider} 实现时才注册。
     * </p>
     * <p>
     * 条件说明：
     * <ul>
     *     <li>{@code @ConditionalOnClass} — 确保 MCP SDK 在 classpath 中（mf-common-ai 已声明依赖）</li>
     *     <li>{@code @ConditionalOnBean(McpServerConfigProvider.class)} — 确保业务层提供了 MCP 配置数据源
     *        （由 mf-ai 模块的 McpServerConfigServiceImpl 实现）</li>
     * </ul>
     * 若条件不满足（如 mf-common-ai 独立测试或 mf-ai 模块未启动），MCP 引擎不注册，不影响其他子引擎。
     * </p>
     */
    @Bean
    @ConditionalOnClass(name = "io.modelcontextprotocol.client.McpSyncClient")
    @ConditionalOnBean(McpServerConfigProvider.class)
    public McpCapabilityEngine mcpCapabilityEngine(McpServerConfigProvider configProvider) {
        return new McpCapabilityEngine(configProvider);
    }

    /**
     * 能力引擎初始化触发器：在所有单例 Bean（含所有 CapabilitySubEngine）就绪后，
     * 将子引擎注册到 CapabilityEngine 并构建动作索引
     * <p>
     * 放在此处而非 CapabilityEngine 内，避免 CapabilityEngine 依赖 Spring 容器回调接口，保持可测试性。
     * 与 ApiToolAutoConfiguration 的 apiToolEngineInitializer 模式一致。
     * </p>
     * <p>
     * 对于 McpCapabilityEngine，在注册前先调用 {@code refresh()} 连接所有 MCP 服务器并发现工具，
     * 确保 {@code getActions()} 在注册到 CapabilityEngine 时返回完整的动作列表。
     * </p>
     */
    @Bean
    public SmartInitializingSingleton capabilityEngineInitializer(CapabilityEngine capabilityEngine,
                                                                    List<CapabilitySubEngine> subEngines) {
        return () -> {
            log.info("[CapabilityAutoConfiguration] 发现 {} 个 CapabilitySubEngine: {}",
                    subEngines.size(),
                    subEngines.stream().map(e -> e.getEngineType().name()).toList());

            // MCP 引擎需在注册前初始化（连接 MCP 服务器、发现工具）
            for (CapabilitySubEngine engine : subEngines) {
                if (engine instanceof McpCapabilityEngine mcpEngine) {
                    log.info("[CapabilityAutoConfiguration] 触发 MCP 引擎初始化");
                    mcpEngine.refresh();
                }
            }

            capabilityEngine.registerSubEngines(subEngines);
        };
    }
}
