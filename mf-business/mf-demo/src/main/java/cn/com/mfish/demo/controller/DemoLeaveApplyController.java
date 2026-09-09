package cn.com.mfish.demo.controller;

import cn.com.mfish.common.core.entity.WorkflowCompleteResult;
import cn.com.mfish.common.core.enums.OperateType;
import cn.com.mfish.common.core.web.PageResult;
import cn.com.mfish.common.core.web.ReqPage;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.demo.entity.DemoLeaveApply;
import cn.com.mfish.common.demo.req.ReqDemoLeaveApply;
import cn.com.mfish.common.demo.service.DemoLeaveApplyService;
import cn.com.mfish.common.log.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @description: 请假申请审批Demo
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.4.1
 */
@Slf4j
@Tag(name = "请假申请审批Demo")
@RestController
@RequestMapping("/demoLeaveApply")
public class DemoLeaveApplyController {
    @Resource
    private DemoLeaveApplyService demoLeaveApplyService;

    /**
     * 分页列表查询
     *
     * @param reqDemoLeaveApply 请假申请请求参数
     * @param reqPage           分页参数
     * @return 返回请假申请分页列表
     */
    @Operation(summary = "请假申请审批Demo-分页列表查询", description = "请假申请审批Demo-分页列表查询")
    @GetMapping
    public Result<PageResult<DemoLeaveApply>> queryPageList(ReqDemoLeaveApply reqDemoLeaveApply, ReqPage reqPage) {
        return demoLeaveApplyService.queryPageList(reqDemoLeaveApply, reqPage);
    }

    /**
     * 添加请假申请
     *
     * @param demoLeaveApply 请假申请对象
     * @return 返回添加结果
     */
    @Log(title = "请假申请审批Demo-添加", operateType = OperateType.INSERT)
    @Operation(summary = "新增请假申请草稿",
            description = """
                    【业务流程】新增请假申请草稿单据（auditState=-1 草稿状态，不会启动审批）。
                    提交审批需另行调用"提交请假审批"接口。
                    【前置条件】无
                    【必填字段】
                    - title: 请假标题
                    - leaveType: 请假类型（1=事假 2=病假 3=年假）
                    - startTime: 开始时间（格式 yyyy-MM-dd HH:mm:ss）
                    - endTime: 结束时间（格式 yyyy-MM-dd HH:mm:ss）
                    - reason: 请假原因
                    【可选字段】leaveDays 可不填，系统按 startTime/endTime 自动计算
                    【后续步骤】调用成功后，从返回结果中取 id，调用"提交请假审批"接口启动审批流程
                    """)
    @PostMapping
    public Result<DemoLeaveApply> add(@RequestBody DemoLeaveApply demoLeaveApply) {
        return demoLeaveApplyService.add(demoLeaveApply);
    }

    /**
     * 编辑请假申请
     *
     * @param demoLeaveApply 请假申请对象
     * @return 返回编辑结果
     */
    @Log(title = "请假申请审批Demo-编辑", operateType = OperateType.UPDATE)
    @Operation(summary = "请假申请审批Demo-编辑")
    @PutMapping
    public Result<DemoLeaveApply> edit(@RequestBody DemoLeaveApply demoLeaveApply) {
        return demoLeaveApplyService.edit(demoLeaveApply);
    }

    /**
     * 通过id删除请假申请
     *
     * @param id 唯一ID
     * @return 返回删除结果
     */
    @Log(title = "请假申请审批Demo-通过id删除", operateType = OperateType.DELETE)
    @Operation(summary = "请假申请审批Demo-通过id删除")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@Parameter(name = "id", description = "唯一ID") @PathVariable String id) {
        return demoLeaveApplyService.delete(id);
    }

    /**
     * 批量删除请假申请
     *
     * @param ids 批量ID，多个ID以逗号分隔
     * @return 返回删除结果
     */
    @Log(title = "请假申请审批Demo-批量删除", operateType = OperateType.DELETE)
    @Operation(summary = "请假申请审批Demo-批量删除")
    @DeleteMapping("/batch/{ids}")
    public Result<Boolean> deleteBatch(@Parameter(name = "ids", description = "唯一ID") @PathVariable String ids) {
        return demoLeaveApplyService.deleteBatch(ids);
    }

    /**
     * 通过id查询请假申请
     *
     * @param id 唯一ID
     * @return 返回请假申请对象
     */
    @Operation(summary = "查询请假申请详情",
            description = """
                    【业务流程】根据单据 ID 查询请假申请详情，包含审批状态、请假信息等。
                    【用途】用于提交审批后查询当前状态（-1=草稿 0=审核中 1=已通过 2=已驳回）
                    【参数】id 为请假申请单据 ID
                    """)
    @GetMapping("/{id}")
    public Result<DemoLeaveApply> queryById(@Parameter(name = "id", description = "唯一ID") @PathVariable String id) {
        return demoLeaveApplyService.queryById(id);
    }

    /**
     * 导出请假申请数据
     *
     * @param reqDemoLeaveApply 请假申请请求参数
     * @param reqPage           分页参数
     * @throws IOException IO异常
     */
    @Operation(summary = "导出请假申请审批Demo", description = "导出请假申请审批Demo")
    @GetMapping("/export")
    public void export(ReqDemoLeaveApply reqDemoLeaveApply, ReqPage reqPage) throws IOException {
        demoLeaveApplyService.export(reqDemoLeaveApply, reqPage);
    }

    /**
     * 提交请假申请审批，启动工作流流程
     *
     * @param id 请假申请ID
     * @return 返回提交结果
     */
    @Log(title = "请假申请审批Demo-提交审批", operateType = OperateType.UPDATE)
    @Operation(summary = "提交请假审批",
            description = """
                    【业务流程】提交请假申请进入审批流程，启动工作流。
                    会将单据状态从草稿(-1)变为审核中(0)，并按 BPMN 流程定义流转到第一个审批节点。
                    【前置条件】必须先调用"新增请假申请草稿"接口创建单据，获取返回的 id
                    【参数】id 为"新增请假申请草稿"接口返回的单据 ID
                    【后续步骤】流程启动后，等待审批人在待办列表中处理；可通过"查询请假申请"接口查看状态
                    """)
    @PostMapping("/submit/{id}")
    public Result<DemoLeaveApply> submit(@PathVariable String id) {
        return demoLeaveApplyService.submit(id);
    }

    /**
     * 撤回请假申请审批，终止工作流流程
     *
     * @param id 请假申请ID
     * @return 返回撤回结果
     */
    @Log(title = "请假申请审批Demo-撤回审批", operateType = OperateType.UPDATE)
    @Operation(summary = "撤回请假审批",
            description = """
                    【业务流程】撤回已提交的请假审批，终止工作流流程。
                    会将单据状态从审核中(0)恢复为草稿(-1)。
                    【前置条件】该请假申请已通过"提交请假审批"接口提交，且当前处于审核中状态
                    【参数】id 为请假申请单据 ID
                    """)
    @PostMapping("/revoke/{id}")
    public Result<DemoLeaveApply> revoke(@PathVariable String id) {
        return demoLeaveApplyService.revoke(id);
    }

    /**
     * 审批通过回调接口
     *
     * @param id     请假申请ID
     * @param result 工作流完成结果
     * @return 返回审批结果
     */
    @PostMapping("/approved/{id}")
    public Result<String> approved(@PathVariable String id, @RequestBody WorkflowCompleteResult result) {
        return demoLeaveApplyService.audit(id, 1, result);
    }

    /**
     * 审批驳回回调接口
     *
     * @param id     请假申请ID
     * @param result 工作流完成结果
     * @return 返回审批结果
     */
    @PostMapping("/rejected/{id}")
    public Result<String> rejected(@PathVariable String id, @RequestBody WorkflowCompleteResult result) {
        return demoLeaveApplyService.audit(id, 2, result);
    }

    /**
     * 审批取消回调接口
     *
     * @param id     请假申请ID
     * @param result 工作流完成结果
     * @return 返回审批结果
     */
    @PostMapping("/canceled/{id}")
    public Result<String> canceled(@PathVariable String id, @RequestBody WorkflowCompleteResult result) {
        return demoLeaveApplyService.audit(id, null, result);
    }
}
