package cn.com.mfish.common.ai.capability;

import cn.com.mfish.common.ai.engine.ApiToolEngine;
import cn.com.mfish.common.core.utils.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport;
import org.springframework.ai.mcp.client.webflux.transport.WebFluxSseClientTransport;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP 能力引擎
 * <p>
 * 实现 {@link CapabilitySubEngine} 接口，对接外部 MCP (Model Context Protocol) Server，
 * 将远程工具转换为统一的 {@link ActionDefinition}。
 * </p>
 * <p>
 * <b>架构定位</b>：
 * <pre>
 * CapabilityEngine（门面）
 *     │
 *     ├── ToolCapabilityEngine   (Feign/OpenAPI)
 *     ├── McpCapabilityEngine    (本类，MCP 协议)
 *     ├── [SkillCapabilityEngine]  (未来扩展)
 *     └── [WorkflowCapabilityEngine] (未来扩展)
 * </pre>
 * </p>
 * <p>
 * <b>初始化流程</b>：
 * <ol>
 *   <li>从 {@link McpServerConfigProvider} 获取所有活跃的 MCP 服务器配置</li>
 *   <li>为每个配置创建 {@link McpSyncClient}：
 *     <ul>
 *       <li>stdio Transport：通过 {@link StdioClientTransport} 拉起本地进程（Node.js/Python）</li>
 *       <li>SSE Transport：通过 {@link WebFluxSseClientTransport} 连接远程 MCP 服务（已 deprecated）</li>
 *       <li>Streamable HTTP Transport：通过 {@link WebClientStreamableHttpTransport} 连接远程 MCP 服务（MCP 2025-03-26 规范，推荐）</li>
 *     </ul>
 *   </li>
 *   <li>调用 {@code client.initialize()} + {@code client.listTools()} 获取工具列表</li>
 *   <li>将工具映射为 {@link ActionDefinition}，动作名加 {@code mcp.{serverName}.} 前缀避免冲突</li>
 * </ol>
 * </p>
 * <p>
 * <b>动作名约定</b>：{@code mcp.{serverName}.{toolName}}
 * <br>示例：{@code mcp.filesystem.readFile}、{@code mcp.github.searchRepos}
 * </p>
 * <p>
 * <b>线程安全</b>：clients 和 actions 映射使用 ConcurrentHashMap，支持并发读取。
 * 初始化通过 {@link #refreshAsync(CapabilityEngine)} 在 daemon 线程异步执行，
 * 不阻塞 Spring Boot 主线程。初始化完成后回调重建动作索引。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Slf4j
@SuppressWarnings("deprecation")
public class McpCapabilityEngine implements CapabilitySubEngine {

    /**
     * stdio 传输类型标识
     */
    private static final String TRANSPORT_STDIO = "stdio";

    /**
     * SSE 传输类型标识
     */
    private static final String TRANSPORT_SSE = "sse";

    /**
     * Streamable HTTP 传输类型标识（MCP 2025-03-26 规范，推荐用于新服务器）
     */
    private static final String TRANSPORT_STREAMABLE = "streamable";

    /**
     * MCP 动作名前缀
     */
    private static final String MCP_ACTION_PREFIX = "mcp.";

    /**
     * MCP 服务器配置提供者（由业务层注入，查询数据库）
     */
    private final McpServerConfigProvider configProvider;

    /**
     * ApiToolEngine 引用：MCP 工具发现后包装为 ToolCallback 注册到 ApiToolEngine，
     * 使 BaseAssistant 能通过 apiToolEngine.getToolCallbackProvider(serviceIds) 获取 MCP 工具
     */
    private final ApiToolEngine apiToolEngine;

    /**
     * MCP 客户端映射：serverName → McpSyncClient
     */
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /**
     * 动作映射：actionName → McpActionEntry（含 client 引用和原始 toolName）
     */
    private final Map<String, McpActionEntry> actionRegistry = new ConcurrentHashMap<>();

    /**
     * 已映射的 ActionDefinition 列表（不可变快照，getActions() 直接返回）
     */
    private volatile List<ActionDefinition> cachedActions = Collections.emptyList();

    /**
     * 初始化状态标志：true 表示已完成一次 refresh（无论成功与否）
     * <p>
     * 异步初始化期间为 false，{@link #execute} 会据此返回友好提示。
     * </p>
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * 初始化中标志：防止并发重复触发 refresh
     */
    private final AtomicBoolean initializing = new AtomicBoolean(false);

    public McpCapabilityEngine(McpServerConfigProvider configProvider, ApiToolEngine apiToolEngine) {
        this.configProvider = configProvider;
        this.apiToolEngine = apiToolEngine;
    }

    @Override
    public EngineType getEngineType() {
        return EngineType.MCP;
    }

    /**
     * 获取所有 MCP 服务器的工具列表（并集）
     * <p>
     * 返回的是初始化时缓存的快照，不会实时调用 MCP 服务器。
     * 如需刷新，调用 {@link #refresh()}。
     * </p>
     */
    @Override
    public List<ActionDefinition> getActions() {
        return cachedActions;
    }

    /**
     * 执行 MCP 工具动作
     * <p>
     * 按 actionName 从 actionRegistry 查找对应的 McpSyncClient 和原始 toolName，
     * 调用 {@code client.callTool(CallToolRequest)} 执行工具。
     * </p>
     */
    @Override
    public ActionResult execute(String actionName, Map<String, Object> params, ExecutionContext ctx) {
        long start = System.currentTimeMillis();
        // 异步初始化尚未完成时，拒绝执行并返回友好提示
        if (!initialized.get()) {
            return ActionResult.failure(EngineType.MCP,
                    "MCP 引擎正在异步初始化中，请稍后重试: " + actionName, System.currentTimeMillis() - start);
        }
        McpActionEntry entry = actionRegistry.get(actionName);
        if (entry == null) {
            return ActionResult.failure(EngineType.MCP,
                    "未找到 MCP 动作: " + actionName, System.currentTimeMillis() - start);
        }

        try {
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                    entry.toolName, params != null ? params : Collections.emptyMap());
            McpSchema.CallToolResult result = entry.client.callTool(request);
            String output = extractTextContent(result);
            return ActionResult.success(EngineType.MCP, output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[McpCapabilityEngine] 执行 MCP 工具失败 action={} tool={}",
                    actionName, entry.toolName, e);
            return ActionResult.failure(EngineType.MCP,
                    "MCP 工具执行异常: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 异步初始化：在后台线程执行 {@link #refresh()}，不阻塞主线程（Spring Boot 启动）。
     * <p>
     * 初始化完成后回调 {@code capabilityEngine.refreshActionIndex()} 重建动作索引，
     * 使 MCP 工具对 Planner 可见。
     * </p>
     * <p>
     * 使用 daemon 线程，JVM 退出时自动终止；通过 {@link #initializing} 标志防止并发重复触发。
     * </p>
     *
     * @param capabilityEngine 能力引擎门面（初始化完成后回调重建索引）
     */
    public void refreshAsync(CapabilityEngine capabilityEngine) {
        if (!initializing.compareAndSet(false, true)) {
            log.info("[McpCapabilityEngine] 初始化已在进行中，跳过重复触发");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                log.info("[McpCapabilityEngine] 异步初始化开始（不阻塞主线程）");
                refresh();
            } catch (Exception e) {
                log.error("[McpCapabilityEngine] 异步初始化异常", e);
            } finally {
                initialized.set(true);
                initializing.set(false);
                // 初始化完成后重建动作索引，使 MCP 工具对 Planner 可见
                if (capabilityEngine != null) {
                    try {
                        capabilityEngine.refreshActionIndex();
                        log.info("[McpCapabilityEngine] 动作索引已重建，MCP 工具现已可用");
                    } catch (Exception e) {
                        log.error("[McpCapabilityEngine] 重建动作索引失败", e);
                    }
                }
            }
        }, "mcp-capability-engine-init");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 同步初始化：连接所有 MCP 服务器，发现工具，构建动作注册表
     * <p>
     * 由 {@link #refreshAsync(CapabilityEngine)} 在后台线程调用，或供外部手动触发。
     * 某个服务器连接失败不影响其他服务器。
     * </p>
     */
    public synchronized void refresh() {
        if (configProvider == null) {
            log.warn("[McpCapabilityEngine] 无 McpServerConfigProvider，跳过初始化");
            return;
        }

        List<McpServerInfo> configs;
        try {
            configs = configProvider.getActiveServerConfigs();
        } catch (Exception e) {
            log.error("[McpCapabilityEngine] 获取 MCP 配置失败", e);
            return;
        }

        if (configs == null || configs.isEmpty()) {
            log.info("[McpCapabilityEngine] 无活跃的 MCP 服务器配置");
            clearAll();
            return;
        }

        // 清理旧的连接和动作
        clearAll();

        List<ActionDefinition> actions = new ArrayList<>();
        for (McpServerInfo config : configs) {
            try {
                McpSyncClient client = connectServer(config);
                if (client == null) {
                    continue;
                }
                clients.put(config.getServerName(), client);

                // 列出工具并映射为 ActionDefinition + McpToolCallback
                McpSchema.ListToolsResult toolsResult = client.listTools();
                if (toolsResult == null || toolsResult.tools() == null) {
                    continue;
                }
                List<ToolCallback> serverCallbacks = new ArrayList<>();
                for (McpSchema.Tool tool : toolsResult.tools()) {
                    String actionName = buildActionName(config.getServerName(), tool.name());
                    String inputSchema = serializeSchema(tool.inputSchema());
                    ActionDefinition action = new ActionDefinition()
                            .setName(actionName)
                            .setDescription(tool.description())
                            .setInputSchema(inputSchema)
                            .setEngineType(EngineType.MCP)
                            .setServiceId(config.getServerName());
                    actions.add(action);
                    actionRegistry.put(actionName, new McpActionEntry(client, tool.name()));
                    // 包装为 ToolCallback，使 BaseAssistant 能通过 ApiToolEngine 获取到 MCP 工具
                    serverCallbacks.add(new McpToolCallback(actionName, tool.description(),
                            inputSchema, client, tool.name()));
                    log.info("[McpCapabilityEngine] 注册 MCP 工具 server={} tool={} action={}",
                            config.getServerName(), tool.name(), actionName);
                }
                // 将该 MCP 服务器的工具注册到 ApiToolEngine（replace 覆盖旧数据，支持动态刷新）
                if (apiToolEngine != null && !serverCallbacks.isEmpty()) {
                    apiToolEngine.replace(config.getServerName(), serverCallbacks);
                }
                log.info("[McpCapabilityEngine] MCP 服务器 {} 连接成功，注册 {} 个工具",
                        config.getServerName(), toolsResult.tools().size());
            } catch (Exception e) {
                log.error("[McpCapabilityEngine] MCP 服务器 {} 连接失败", config.getServerName(), e);
            }
        }

        this.cachedActions = Collections.unmodifiableList(actions);
        log.info("[McpCapabilityEngine] 初始化完成，共 {} 个 MCP 服务器，{} 个工具",
                clients.size(), actions.size());
    }

    /**
     * 连接单个 MCP 服务器（支持 stdio / SSE / Streamable HTTP 三种 Transport）
     */
    private McpSyncClient connectServer(McpServerInfo config) {
        String transportType = config.getTransportType();
        if (TRANSPORT_STDIO.equalsIgnoreCase(transportType)) {
            return connectStdio(config);
        } else if (TRANSPORT_SSE.equalsIgnoreCase(transportType)) {
            return connectSse(config);
        } else if (TRANSPORT_STREAMABLE.equalsIgnoreCase(transportType)) {
            return connectStreamable(config);
        } else {
            log.warn("[McpCapabilityEngine] 不支持的传输类型: {} server={}", transportType, config.getServerName());
            return null;
        }
    }

    /**
     * stdio Transport：拉起本地进程
     */
    private McpSyncClient connectStdio(McpServerInfo config) {
        if (StringUtils.isEmpty(config.getCommand())) {
            log.warn("[McpCapabilityEngine] stdio 模式缺少 command 参数 server={}", config.getServerName());
            return null;
        }

        // Windows 平台适配：ProcessBuilder 不会自动解析 .cmd 扩展名（npx/npm/yarn 等）
        String resolvedCommand = resolveWindowsCommand(config.getCommand());
        ServerParameters.Builder builder = ServerParameters.builder(resolvedCommand);

        // 解析 args（JSON 数组字符串 → List<String>）
        if (StringUtils.isNotEmpty(config.getArgs())) {
            try {
                List<String> argsList = JSON.parseArray(config.getArgs(), String.class);
                builder.args(argsList);
            } catch (Exception e) {
                log.warn("[McpCapabilityEngine] 解析 args 失败 server={} args={}",
                        config.getServerName(), config.getArgs(), e);
            }
        }

        // 解析 env（JSON 对象字符串 → Map<String, String>）
        if (StringUtils.isNotEmpty(config.getEnv())) {
            try {
                Map<String, String> envMap = JSON.parseObject(config.getEnv(), new TypeReference<>() {
                });
                builder.env(envMap);
            } catch (Exception e) {
                log.warn("[McpCapabilityEngine] 解析 env 失败 server={} env={}",
                        config.getServerName(), config.getEnv(), e);
            }
        }

        // MCP SDK 2.0.0：StdioClientTransport 构造函数需要 McpJsonMapper 参数
        McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();
        StdioClientTransport transport = new StdioClientTransport(builder.build(), jsonMapper);
        return createAndInitialize(transport, config.getServerName());
    }

    /**
     * Windows 平台命令适配
     * <p>
     * Windows 下 Java 的 {@link ProcessBuilder} 不会自动解析 {@code .cmd}/{@code .bat} 扩展名，
     * 导致 {@code npx}/{@code npm}/{@code yarn} 等命令（实际为 {@code npx.cmd} 文件）无法启动。
     * 本方法在 Windows 平台上遍历 PATH 环境变量，查找 {@code {command}.cmd} 的完整路径。
     * </p>
     * <p>
     * 非 Windows 平台、已含路径分隔符、已含扩展名的命令均原样返回。
     * </p>
     *
     * @param command 原始命令名（如 {@code npx}）
     * @return 适配后的命令（如 {@code D:\Program Files\nodejs\npx.cmd}），或原样返回
     */
    private String resolveWindowsCommand(String command) {
        // 非 Windows 平台，原样返回
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return command;
        }
        // 已包含路径分隔符或扩展名，原样返回
        if (command.contains(File.separator) || command.contains(".")) {
            return command;
        }
        // 在 PATH 中查找 {command}.cmd
        String path = System.getenv("PATH");
        if (path == null || path.isEmpty()) {
            return command;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            File cmdFile = new File(dir, command + ".cmd");
            if (cmdFile.exists()) {
                String absolutePath = cmdFile.getAbsolutePath();
                log.info("[McpCapabilityEngine] Windows 命令适配: {} → {}", command, absolutePath);
                return absolutePath;
            }
        }
        return command;
    }

    /**
     * SSE Transport：连接远程 MCP 服务
     */
    private McpSyncClient connectSse(McpServerInfo config) {
        if (StringUtils.isEmpty(config.getSseUrl())) {
            log.warn("[McpCapabilityEngine] SSE 模式缺少 sseUrl 参数 server={}", config.getServerName());
            return null;
        }

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(config.getSseUrl());

        // 注入认证 Token
        if (StringUtils.isNotEmpty(config.getAuthToken())) {
            webClientBuilder.defaultHeader("Authorization", "Bearer " + config.getAuthToken());
        }

        WebFluxSseClientTransport.Builder transportBuilder = WebFluxSseClientTransport.builder(webClientBuilder);
        if (StringUtils.isNotEmpty(config.getSseEndpoint())) {
            transportBuilder.sseEndpoint(config.getSseEndpoint());
        }

        WebFluxSseClientTransport transport = transportBuilder.build();
        return createAndInitialize(transport, config.getServerName());
    }

    /**
     * Streamable HTTP Transport：连接远程 MCP 服务（MCP 2025-03-26 规范，推荐用于新服务器）
     * <p>
     * 与 SSE 的区别：
     * <ul>
     *   <li>单一 /mcp 端点，请求可流式响应也可普通响应</li>
     *   <li>支持会话恢复（resumableStreams）</li>
     *   <li>支持协议版本协商（supportedProtocolVersions）</li>
     *   <li>是 MCP 2025-03-26 规范的标准 transport，SSE 已标记 deprecated</li>
     * </ul>
     * </p>
     * <p>
     * 字段复用：sseUrl 作为基础 URL，sseEndpoint 作为端点路径（通常为 /mcp）。
     * </p>
     */
    private McpSyncClient connectStreamable(McpServerInfo config) {
        if (StringUtils.isEmpty(config.getSseUrl())) {
            log.warn("[McpCapabilityEngine] streamable 模式缺少 sseUrl 参数 server={}", config.getServerName());
            return null;
        }

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(config.getSseUrl());

        // 注入认证 Token
        if (StringUtils.isNotEmpty(config.getAuthToken())) {
            webClientBuilder.defaultHeader("Authorization", "Bearer " + config.getAuthToken());
        }

        WebClientStreamableHttpTransport.Builder transportBuilder = WebClientStreamableHttpTransport.builder(webClientBuilder);
        if (StringUtils.isNotEmpty(config.getSseEndpoint())) {
            transportBuilder.endpoint(config.getSseEndpoint());
        }

        WebClientStreamableHttpTransport transport = transportBuilder.build();
        return createAndInitialize(transport, config.getServerName());
    }

    /**
     * 创建 McpSyncClient 并初始化连接
     * <p>
     * 超时说明：
     * <ul>
     *   <li>{@code initializationTimeout(120s)} — initialize() 握手超时，stdio 模式首次 npx 下载包较慢</li>
     *   <li>{@code requestTimeout(60s)} — 工具调用（listTools/callTool）超时</li>
     * </ul>
     * MCP SDK 2.0.0 中这两个超时是独立的，initialize 默认只有 20s，stdio 拉起本地进程场景容易超时。
     * </p>
     */
    private McpSyncClient createAndInitialize(McpClientTransport transport, String serverName) {
        McpSyncClient client = McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(120))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        client.initialize();
        log.info("[McpCapabilityEngine] MCP 客户端已初始化 server={}", serverName);
        return client;
    }

    /**
     * 构建 MCP 动作名：mcp.{serverName}.{toolName}
     */
    private String buildActionName(String serverName, String toolName) {
        return MCP_ACTION_PREFIX + serverName + "." + toolName;
    }

    /**
     * 从 CallToolResult 提取文本内容
     */
    private String extractTextContent(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent tc) {
                sb.append(tc.text());
            } else {
                // 非 TextContent 类型，序列化为 JSON
                sb.append(JSON.toJSONString(content));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 将 MCP Tool 的 inputSchema 序列化为 JSON 字符串
     */
    private String serializeSchema(Object inputSchema) {
        if (inputSchema == null) {
            return "{}";
        }
        try {
            return JSON.toJSONString(inputSchema);
        } catch (Exception e) {
            log.warn("[McpCapabilityEngine] 序列化 inputSchema 失败", e);
            return "{}";
        }
    }

    /**
     * 清理所有连接和动作注册表
     */
    private void clearAll() {
        for (Map.Entry<String, McpSyncClient> entry : clients.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("[McpCapabilityEngine] 关闭 MCP 客户端失败 server={}", entry.getKey(), e);
            }
            // 清理 ApiToolEngine 中该 MCP server 的工具注册
            if (apiToolEngine != null) {
                apiToolEngine.replace(entry.getKey(), List.of());
            }
        }
        clients.clear();
        actionRegistry.clear();
        cachedActions = Collections.emptyList();
    }

    /**
     * MCP 动作注册表条目：持有 McpSyncClient 引用和原始 toolName
     */
    private static class McpActionEntry {
        final McpSyncClient client;
        final String toolName;

        McpActionEntry(McpSyncClient client, String toolName) {
            this.client = client;
            this.toolName = toolName;
        }
    }

    /**
     * MCP 工具 → Spring AI ToolCallback 适配器
     * <p>
     * 将 MCP 远程工具包装为 {@link ToolCallback}，使 BaseAssistant 能通过
     * {@code apiToolEngine.getToolCallbackProvider(serviceIds)} 获取到 MCP 工具，
     * 并通过 {@code chatClient.tools(toolProvider)} 注册给 LLM 进行工具调用。
     * </p>
     * <p>
     * LLM 调用工具时，Spring AI 框架会调用 {@link #call(String)} 方法，
     * 传入 JSON 格式的工具参数，本类解析后委托 {@link McpSyncClient#callTool} 执行。
     * </p>
     */
    private static class McpToolCallback implements ToolCallback {

        private final McpSyncClient client;
        private final String originalToolName;
        private final ToolDefinition toolDefinition;

        /**
         * @param actionName       动作名（mcp.{serverName}.{toolName}，即 LLM 看到的工具名）
         * @param description      工具描述
         * @param inputSchema      输入参数 JSON Schema
         * @param client           MCP 同步客户端
         * @param originalToolName MCP 服务器上的原始工具名
         */
        McpToolCallback(String actionName, String description, String inputSchema,
                        McpSyncClient client, String originalToolName) {
            this.client = client;
            this.originalToolName = originalToolName;
            this.toolDefinition = DefaultToolDefinition.builder()
                    .name(actionName)
                    .description(description != null ? description : actionName)
                    .inputSchema(inputSchema != null ? inputSchema : "{}")
                    .build();
        }

        @Override
        public @NonNull ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public @NonNull String call(@NonNull String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public @NonNull String call(@NonNull String toolInput, ToolContext context) {
            try {
                // 解析 LLM 传入的 JSON 参数
                Map<String, Object> params;
                if (toolInput.isBlank()) {
                    params = Collections.emptyMap();
                } else {
                    params = JSON.parseObject(toolInput, new TypeReference<>() {});
                    if (params == null) {
                        params = Collections.emptyMap();
                    }
                }
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(originalToolName, params);
                McpSchema.CallToolResult result = client.callTool(request);
                return extractTextContent(result);
            } catch (Exception e) {
                log.error("[McpToolCallback] MCP 工具调用失败 tool={} input={}", originalToolName, toolInput, e);
                return "MCP 工具调用失败: " + e.getMessage();
            }
        }

        /**
         * 从 CallToolResult 提取文本内容
         */
        private String extractTextContent(McpSchema.CallToolResult result) {
            if (result == null || result.content() == null || result.content().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent tc) {
                    sb.append(tc.text());
                } else {
                    sb.append(JSON.toJSONString(content));
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }
    }
}
