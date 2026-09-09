package cn.com.mfish.common.ai.capability;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * MCP 服务器配置信息（mf-common-ai 模块内的 DTO）
 * <p>
 * 与 {@code McpServerConfig} 实体类解耦：mf-common-ai 不依赖 mf-ai-api 模块，
 * 通过 {@link McpServerConfigProvider} 接口由业务层注入配置数据。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
@Data
@Accessors(chain = true)
public class McpServerInfo {

    /**
     * MCP 服务器名称（唯一标识）
     */
    private String serverName;

    /**
     * 传输类型：stdio / sse / streamable
     * <ul>
     *   <li>stdio — 拉起本地进程（Node.js/Python）</li>
     *   <li>sse — SSE Transport（MCP 2024-11-05 规范，已 deprecated）</li>
     *   <li>streamable — Streamable HTTP Transport（MCP 2025-03-26 规范，推荐）</li>
     * </ul>
     */
    private String transportType;

    /**
     * stdio 模式启动命令（如 node / python）
     */
    private String command;

    /**
     * stdio 模式参数（JSON 数组字符串，如 ["server.js","--port","3000"]）
     */
    private String args;

    /**
     * stdio 模式环境变量（JSON 对象字符串）
     */
    private String env;

    /**
     * 远程 MCP 服务基础 URL（sse / streamable 模式通用，如 https://mcp.example.com）
     */
    private String sseUrl;

    /**
     * 远程 MCP 服务端点路径（sse / streamable 模式通用，如 /sse 或 /mcp）
     */
    private String sseEndpoint;

    /**
     * 认证 Token（Bearer）
     */
    private String authToken;
}
