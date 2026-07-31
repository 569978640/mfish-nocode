package cn.com.mfish.common.ai.capability;

import java.util.List;

/**
 * MCP 服务器配置提供者接口
 * <p>
 * 跨模块解耦：mf-common-ai 模块的 {@link McpCapabilityEngine} 通过此接口获取数据库中的 MCP 配置，
 * 由 mf-ai 业务模块实现（查询 ai_mcp_server_config 表）。
 * </p>
 * <p>
 * 若 Spring 容器中无此接口的实现 Bean（如 mf-common-ai 独立测试场景），
 * {@link McpCapabilityEngine} 不报错，返回空动作列表。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/21
 */
public interface McpServerConfigProvider {

    /**
     * 获取所有状态为"正常"的 MCP 服务器配置
     *
     * @return MCP 服务器配置列表，无配置时返回空列表
     */
    List<McpServerInfo> getActiveServerConfigs();
}
