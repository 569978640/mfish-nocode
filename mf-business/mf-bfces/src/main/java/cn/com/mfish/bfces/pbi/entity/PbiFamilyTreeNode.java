package cn.com.mfish.bfces.pbi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: PBI产品家谱树节点（自底向上递归查询后组装的衍生关系树）。
 * doc/dpf/scrAsc 为 STRINGVALUE.VALUE2 上的规格软属性，无值时为null；
 * shipment（实际发货量）来自接口库 BFCES_INTERFACE.V_WINDCHILL_SHIPMENT 视图，
 * 按部件编号关联，视图无记录或接口库不可用时为null（前端展示0）。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.2.0
 */
@Data
@Schema(description = "PBI产品家谱树节点")
public class PbiFamilyTreeNode {

    @Schema(description = "Windchill部件ID（WTPART.IDA2A2）")
    private String partId;

    @Schema(description = "部件编号（WTPARTMASTER.WTPARTNUMBER）")
    private String wtPartNumber;

    @Schema(description = "生命周期状态（WTPART.STATESTATE，如 RELEASED/OBSOLETE）")
    private String state;

    @Schema(description = "部件图片静态访问地址（按部件编号拼接，编号非法或未配置域名时为空串，前端回退默认图标）")
    private String image;

    @Schema(description = "DOC规格（软属性 com.bfces.DOC 的 VALUE2，无值为null）")
    private String doc;

    @Schema(description = "DPF规格（软属性 com.bfces.DPF 的 VALUE2，无值为null）")
    private String dpf;

    @Schema(description = "SCR规格（软属性 com.bfces.SCR_ASC 的 VALUE2，无值为null）")
    private String scrAsc;

    @Schema(description = "实际发货量（接口库 V_WINDCHILL_SHIPMENT.SHIPMENT，按部件编号关联；无记录或接口库不可用时为null，前端展示0）")
    private String shipment;

    @Schema(description = "下一代衍生产品节点（由该节点衍生出的子型号）")
    private List<PbiFamilyTreeNode> children = new ArrayList<>();
}
