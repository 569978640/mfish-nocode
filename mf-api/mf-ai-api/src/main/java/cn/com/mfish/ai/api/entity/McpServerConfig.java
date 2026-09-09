package cn.com.mfish.ai.api.entity;

import cn.com.mfish.common.core.entity.BaseEntity;
import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @description: MCP服务器配置信息
 * @author: mfish
 * @date: 2026-07-21
 * @version: V2.4.1
 */
@Data
@TableName("ai_mcp_server_config")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "ai_mcp_server_config对象 MCP服务器配置信息")
public class McpServerConfig extends BaseEntity<String> {
    @ExcelProperty("唯一ID")
    @Schema(description = "唯一ID")
    @TableId(type = IdType.ASSIGN_UUID)
    @Accessors(chain = true)
    private String id;
    @ExcelProperty("MCP服务器名称")
    @Schema(description = "MCP服务器名称")
    private String serverName;
    @ExcelProperty("传输类型: stdio/sse/streamable")
    @Schema(description = "传输类型: stdio/sse/streamable")
    private String transportType;
    @ExcelProperty("stdio模式启动命令")
    @Schema(description = "stdio模式启动命令(如node/python)")
    private String command;
    @ExcelProperty("stdio模式参数(JSON数组)")
    @Schema(description = "stdio模式参数(JSON数组,如[\"server.js\",\"--port\",\"3000\"])")
    private String args;
    @ExcelProperty("stdio模式环境变量(JSON对象)")
    @Schema(description = "stdio模式环境变量(JSON对象)")
    private String env;
    @ExcelProperty("远程服务基础URL(sse/streamable通用)")
    @Schema(description = "远程服务基础URL(sse/streamable通用,如https://mcp.example.com)")
    private String sseUrl;
    @ExcelProperty("远程服务端点路径(sse/streamable通用)")
    @Schema(description = "远程服务端点路径(sse/streamable通用,如/sse或/mcp)")
    private String sseEndpoint;
    @ExcelProperty("认证Token")
    @Schema(description = "认证Token(Bearer)")
    private String authToken;
    @ExcelProperty("状态 0正常 1停用")
    @Schema(description = "状态 0正常 1停用")
    private Short status;
    @ExcelProperty("备注")
    @Schema(description = "备注")
    private String remark;
}
