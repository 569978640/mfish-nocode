package cn.com.mfish.ai.service.impl;

import cn.com.mfish.common.ai.capability.WorkflowConfigProvider;
import cn.com.mfish.common.ai.capability.WorkflowInfo;
import cn.com.mfish.common.ai.capability.WorkflowVariable;
import cn.com.mfish.common.core.constants.RPCConstants;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.workflow.api.entity.FlowManage;
import cn.com.mfish.common.workflow.api.remote.RemoteWorkflowService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link WorkflowConfigProvider} 业务层实现
 * <p>
 * 通过 {@link RemoteWorkflowService} Feign 调用 mf-workflow 服务的
 * {@code /process/activeFlows} 端点，获取已发布的 BPMN 流程定义，
 * 转换为 {@link WorkflowInfo} 列表供 {@code WorkflowCapabilityEngine} 导出为 LLM 工具。
 * </p>
 * <p>
 * 跨模块解耦：mf-common-ai 不依赖 mf-workflow-api，本实现作为桥接层位于 mf-ai 模块。
 * </p>
 *
 * @author: mfish
 * @date: 2026/07/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowConfigProviderImpl implements WorkflowConfigProvider {

    private final RemoteWorkflowService remoteWorkflowService;

    @Override
    public List<WorkflowInfo> getActiveWorkflows() {
        Result<List<FlowManage>> result;
        try {
            result = remoteWorkflowService.getActiveFlows(RPCConstants.AI);
        } catch (Exception e) {
            log.error("[WorkflowConfigProvider] 调用 mf-workflow 查询已发布流程失败", e);
            return Collections.emptyList();
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.warn("[WorkflowConfigProvider] 查询已发布流程失败: {}", result == null ? "null" : result.getMsg());
            return Collections.emptyList();
        }
        List<FlowManage> flowManageList = result.getData();
        if (flowManageList.isEmpty()) {
            return Collections.emptyList();
        }
        List<WorkflowInfo> workflows = new ArrayList<>(flowManageList.size());
        for (FlowManage flowManage : flowManageList) {
            WorkflowInfo info = new WorkflowInfo()
                    .setFlowKey(flowManage.getFlowKey())
                    .setFlowName(flowManage.getName())
                    .setDescription(flowManage.getRemark());
            // 解析流程配置中的变量定义（若存在）
            List<WorkflowVariable> variables = parseVariables(flowManage.getFlowConfig());
            if (!variables.isEmpty()) {
                info.setVariables(variables);
            }
            workflows.add(info);
        }
        log.info("[WorkflowConfigProvider] 获取到 {} 个已发布流程", workflows.size());
        return workflows;
    }

    /**
     * 从流程配置 JSON 中解析变量定义
     * <p>
     * 流程配置格式由前端 BPMN 编辑器生成，此处尝试从常见字段（variables / formItems / formConfig）
     * 中提取变量定义。若格式不匹配或无变量定义，返回空列表，LLM 将使用自由 variables 参数。
     * </p>
     */
    private List<WorkflowVariable> parseVariables(String flowConfig) {
        if (flowConfig == null || flowConfig.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JSONObject config = JSON.parseObject(flowConfig);
            List<WorkflowVariable> variables = new ArrayList<>();
            // 尝试多种常见字段名，兼容不同版本的前端流程编辑器
            collectVariables(config.getJSONArray("variables"), variables);
            collectVariables(config.getJSONArray("formItems"), variables);
            collectVariables(config.getJSONArray("formConfig"), variables);
            return variables;
        } catch (Exception e) {
            log.debug("[WorkflowConfigProvider] 解析流程配置变量失败，将使用自由 variables 参数", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 JSON 数组中收集变量定义
     */
    private void collectVariables(JSONArray array, List<WorkflowVariable> variables) {
        if (array == null || array.isEmpty()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.getString("name");
            if (name == null || name.isEmpty()) {
                // 兼容 field/id 作为变量名
                name = item.getString("field");
                if (name == null || name.isEmpty()) {
                    name = item.getString("id");
                }
            }
            if (name == null || name.isEmpty()) {
                continue;
            }
            WorkflowVariable var = new WorkflowVariable()
                    .setName(name)
                    .setType(mapVariableType(item.getString("type")))
                    .setDescription(item.getString("description") != null
                            ? item.getString("description")
                            : item.getString("label"))
                    .setRequired(Boolean.TRUE.equals(item.getBoolean("required")));
            variables.add(var);
        }
    }

    /**
     * 将前端表单字段类型映射为 JSON Schema 类型
     */
    private String mapVariableType(String frontendType) {
        if (frontendType == null) {
            return "string";
        }
        return switch (frontendType.toLowerCase()) {
            case "number", "integer", "int", "long", "decimal" -> "number";
            case "boolean", "switch", "checkbox" -> "boolean";
            default -> "string";
        };
    }
}
