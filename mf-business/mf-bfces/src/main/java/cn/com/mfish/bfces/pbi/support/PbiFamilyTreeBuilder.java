package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.entity.PbiFamilyPartSnapshot;
import cn.com.mfish.bfces.pbi.entity.PbiFamilyTreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI产品家谱树构建器。
 * 以二级分类下的底层部件为起点，沿"上一代物料编码"自底向上逐层递归（BASELINE收尾），
 * 对属性值为空、属性行缺失、父部件编号无法解析的支系执行剪枝；
 * 经可见性计算后委托 {@link PbiFamilyPartEnricher} 回填实际发货量，最终组装为
 * 收敛到唯一BASELINE顶层的衍生关系树。
 * 部件数据（叶ID、编号/状态/上一代物料编码/DOC/DPF/SCR）统一由 {@link PbiFamilyPartReader}
 * 从"索引视图 + Redis Hash"读取（异常自动降级WTPART/STRINGVALUE直查），
 * 构建过程的共享状态由内部 {@link FamilyBuildContext} 承载。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiFamilyTreeBuilder {

    /** 上一代物料编码为BASELINE时表示家谱顶层 */
    private static final String BASELINE_VALUE = "BASELINE";

    private final PbiFamilyPartReader familyPartReader;

    private final PbiPartConverter partConverter;

    private final PbiFamilyPartEnricher partEnricher;

    /** 快照 -> 节点展示信息组装器 */
    private final PbiFamilySnapshotAssembler snapshotAssembler;

    /**
     * 构建产品家谱树。
     *
     * @param productClassification 二级分类内部名称（PRODUCTCLASSIFICATION）
     * @return 产品家谱顶层节点；二级分类为空或无任何可达BASELINE顶层的有效支系时返回null
     */
    public PbiFamilyTreeNode build(String productClassification) {
        if (StringUtils.isBlank(productClassification)) {
            log.warn("二级分类为空，终止产品家谱查询");
            return null;
        }
        FamilyBuildContext context = new FamilyBuildContext();

        // 1. 查询二级分类下的底层部件ID（索引视图实时查询+短缓存，去重、去空白，保留返回顺序）
        List<String> leafIdRows = familyPartReader.findLeafIds(productClassification.trim());
        if (leafIdRows == null || leafIdRows.isEmpty()) {
            log.warn("二级分类[{}]下未查询到底层部件，产品家谱为空", productClassification);
            return null;
        }
        for (String leafId : leafIdRows) {
            if (StringUtils.isNotBlank(leafId)) {
                context.visited.add(leafId.trim());
            }
        }
        if (context.visited.isEmpty()) {
            return null;
        }

        // 2. 底层叶节点快照一次性批量加载（属性Hash缓存优先，后续父节点随递归逐层获取）
        Map<String, PbiFamilyPartSnapshot> leafSnapshots =
                familyPartReader.loadSnapshots(new ArrayList<>(context.visited),
                        "family:leaf:" + productClassification.trim());
        mergeSnapshots(leafSnapshots, context);
        for (String leafId : new ArrayList<>(context.visited)) {
            if (!context.nodeInfo.containsKey(leafId)) {
                log.warn("底层部件[{}]未查询到部件信息，该支系不进入家谱", leafId);
                context.excluded.add(leafId);
            }
        }

        // 3. 自底向上逐层递归：沿快照中的上一代物料编码向上，BASELINE收尾，空值/缺失剪枝
        traverseUpwards(productClassification.trim(), context);

        // 4. 可见性计算：自身被剪枝或任意祖先被剪枝的支系均不可见（带循环防护与记忆化）
        for (String partId : context.visited) {
            resolveVisible(partId, context);
        }

        // 5. 收集可见节点，批量回填实际发货量（接口库按编号关联，跨库失败时降级留空）；剪枝节点不查
        List<String> visibleIds = new ArrayList<>();
        for (String partId : context.visited) {
            if (Boolean.TRUE.equals(context.visibleCache.get(partId))) {
                visibleIds.add(partId);
            }
        }
        if (!visibleIds.isEmpty()) {
            partEnricher.enrich(visibleIds, context.nodeInfo);
        }

        // 6. 统一组装树：先收集父->子列表，再从可见顶层节点递归构建DTO
        return assembleTree(productClassification, context);
    }

    /**
     * 将一批部件快照合并到构建上下文：快照入snapshots，展示信息同步写入nodeInfo供树组装使用。
     *
     * @param snapshots 部件ID -> 快照
     * @param context   家谱构建共享状态
     */
    private void mergeSnapshots(Map<String, PbiFamilyPartSnapshot> snapshots, FamilyBuildContext context) {
        if (snapshots == null) {
            return;
        }
        for (PbiFamilyPartSnapshot snapshot : snapshots.values()) {
            if (snapshot == null || StringUtils.isBlank(snapshot.getPartId())) {
                continue;
            }
            String partId = snapshot.getPartId().trim();
            context.snapshots.put(partId, snapshot);
            context.nodeInfo.putIfAbsent(partId, snapshotAssembler.toNodeInfo(snapshot));
        }
    }

    /**
     * 自底向上逐层递归，填充上下文中的父子关系、顶层集合与剪枝集合。
     * childPartId -> parentPartId 衍生关系按发现顺序保存，保证子节点排列稳定。
     *
     * @param productClassification 二级分类内部名称（仅用于回源锁标识与告警）
     * @param context               家谱构建共享状态
     */
    private void traverseUpwards(String productClassification, FamilyBuildContext context) {
        Deque<String> queue = new ArrayDeque<>(context.visited);
        while (!queue.isEmpty()) {
            List<String> currentIds = new ArrayList<>(queue);
            queue.clear();

            // 当前层的"上一代物料编码"直接取自已加载快照，无额外查询
            Map<String, String> previousValueMap = new HashMap<>();
            for (String partId : currentIds) {
                PbiFamilyPartSnapshot snapshot = context.snapshots.get(partId);
                if (snapshot != null && StringUtils.isNotBlank(snapshot.getPreviousMaterialCode())) {
                    previousValueMap.put(partId, snapshot.getPreviousMaterialCode().trim());
                }
            }

            // 待解析的父部件编号（保持顺序去重）与"子部件ID->父编号"待处理关系
            Set<String> parentNumbers = new LinkedHashSet<>();
            Map<String, String> childToParentNumber = new LinkedHashMap<>();
            for (String partId : currentIds) {
                String previousValue = previousValueMap.get(partId);
                if (StringUtils.isBlank(previousValue)) {
                    // 值为空或快照缺失：当前部件及其支系不展示在图谱里
                    log.warn("部件[{}]无上一代物料编码或值为空，该支系不进入家谱", partId);
                    context.excluded.add(partId);
                    continue;
                }
                if (BASELINE_VALUE.equalsIgnoreCase(previousValue)) {
                    context.rootIds.add(partId);
                    continue;
                }
                parentNumbers.add(previousValue);
                childToParentNumber.put(partId, previousValue);
            }

            if (parentNumbers.isEmpty()) {
                continue;
            }
            // 按编号批量解析父部件（索引视图实时解析+属性Hash缓存），同分类递归共用一把回源锁
            Map<String, PbiFamilyPartSnapshot> parents = familyPartReader.findParentSnapshots(
                    new ArrayList<>(parentNumbers), "family:parents:" + productClassification);
            for (Map.Entry<String, String> entry : childToParentNumber.entrySet()) {
                String childId = entry.getKey();
                PbiFamilyPartSnapshot parent = parents.get(entry.getValue());
                if (parent == null || StringUtils.isBlank(parent.getPartId())) {
                    log.warn("子部件[{}]的上一代物料编号[{}]未解析到有效部件，该支系不进入家谱",
                            childId, entry.getValue());
                    context.excluded.add(childId);
                    continue;
                }
                String parentId = parent.getPartId().trim();
                context.parentLink.put(childId, parentId);
                // 同一父部件可能被多个子部件引用，快照/节点信息已存在时不覆盖，visited保证只向上递归一次
                if (!context.nodeInfo.containsKey(parentId)) {
                    context.snapshots.put(parentId, parent);
                    context.nodeInfo.put(parentId, snapshotAssembler.toNodeInfo(parent));
                }
                if (context.visited.add(parentId)) {
                    queue.add(parentId);
                }
            }
        }
    }

    /**
     * 基于上下文中的父子关系与可见性结果，组装最终家谱DTO树。
     * 家谱设计上应收敛到唯一BASELINE顶层，出现多个可见顶层时仅保留首个并告警。
     *
     * @param productClassification 二级分类内部名称（仅用于无有效支系时的告警日志）
     * @param context               家谱构建共享状态
     * @return 家谱顶层节点；无可见顶层时返回null
     */
    private PbiFamilyTreeNode assembleTree(String productClassification, FamilyBuildContext context) {
        Map<String, List<String>> childrenIdsByParent = new LinkedHashMap<>();
        for (Map.Entry<String, String> link : context.parentLink.entrySet()) {
            childrenIdsByParent.computeIfAbsent(link.getValue(), key -> new ArrayList<>()).add(link.getKey());
        }
        PbiFamilyTreeNode result = null;
        for (String rootId : context.rootIds) {
            if (!Boolean.TRUE.equals(context.visibleCache.get(rootId))) {
                continue;
            }
            PbiFamilyTreeNode root = buildFamilyNode(rootId, context, childrenIdsByParent);
            if (root == null) {
                continue;
            }
            if (result == null) {
                result = root;
            } else {
                // 家谱设计上应收敛到唯一BASELINE顶层，出现多个时仅保留首个并告警
                log.warn("产品家谱存在多个BASELINE顶层节点，已返回首个节点[{}]，其余顶层未返回", rootId);
                break;
            }
        }
        if (result == null) {
            log.warn("二级分类[{}]的产品家谱无任何可达BASELINE顶层的有效支系", productClassification);
        }
        return result;
    }

    /**
     * 递归判定部件支系是否可见：自身被剪枝/不在关系链/存在环或祖先不可见时返回false，结果记忆化。
     *
     * @param partId  当前部件ID
     * @param context 家谱构建共享状态（剪枝集合、顶层集合、父子关系、可见性缓存、环路防护）
     * @return 当前部件支系是否可见
     */
    private boolean resolveVisible(String partId, FamilyBuildContext context) {
        Boolean cached = context.visibleCache.get(partId);
        if (cached != null) {
            return cached;
        }
        if (context.excluded.contains(partId)) {
            context.visibleCache.put(partId, false);
            return false;
        }
        if (context.rootIds.contains(partId)) {
            context.visibleCache.put(partId, true);
            return true;
        }
        String parentId = context.parentLink.get(partId);
        if (parentId == null || !context.pathGuard.add(partId)) {
            context.visibleCache.put(partId, false);
            return false;
        }
        boolean visible;
        try {
            visible = resolveVisible(parentId, context);
        } finally {
            context.pathGuard.remove(partId);
        }
        context.visibleCache.put(partId, visible);
        return visible;
    }

    /**
     * 递归组装家谱节点DTO，仅挂载可见且能查到部件信息的子节点。
     *
     * @param partId              当前部件ID
     * @param context             家谱构建共享状态（部件信息与可见性缓存）
     * @param childrenIdsByParent 父部件ID -> 子部件ID有序列表
     * @return 组装完成的家谱节点；部件信息缺失时返回null
     */
    private PbiFamilyTreeNode buildFamilyNode(String partId, FamilyBuildContext context,
                                              Map<String, List<String>> childrenIdsByParent) {
        PbiFamilyTreeNode info = context.nodeInfo.get(partId);
        if (info == null) {
            log.warn("部件[{}]缺少部件信息，无法构建家谱节点", partId);
            return null;
        }
        PbiFamilyTreeNode node = new PbiFamilyTreeNode();
        node.setPartId(info.getPartId());
        node.setWtPartNumber(info.getWtPartNumber());
        node.setState(info.getState());
        // 部件图片地址：与产品列表同一套按编号拼接规则，编号非法/未配置域名时为空串，前端回退默认图标
        node.setImage(partConverter.buildImageUrl(info.getWtPartNumber()));
        // DOC/DPF/SCR_ASC规格（无值为null，前端按null隐藏对应标签）
        node.setDoc(info.getDoc());
        node.setDpf(info.getDpf());
        node.setScrAsc(info.getScrAsc());
        // 实际发货量（接口库按部件编号关联；null 表示视图无记录或接口库不可用，前端展示0）
        node.setShipment(info.getShipment());
        List<String> childIds = childrenIdsByParent.get(partId);
        if (childIds != null) {
            for (String childId : childIds) {
                if (!Boolean.TRUE.equals(context.visibleCache.get(childId))) {
                    continue;
                }
                PbiFamilyTreeNode child = buildFamilyNode(childId, context, childrenIdsByParent);
                if (child != null) {
                    node.getChildren().add(child);
                }
            }
        }
        return node;
    }

    /**
     * 家谱单次构建过程中的共享状态（非线程安全，每次build新建，不跨请求复用）。
     */
    private static class FamilyBuildContext {

        /** 已发现的全部部件ID（保持发现顺序，保证每个部件只向上递归一次） */
        private final Set<String> visited = new LinkedHashSet<>();

        /** 被剪枝、不进入家谱的部件ID */
        private final Set<String> excluded = new HashSet<>();

        /** BASELINE顶层部件ID（保持发现顺序） */
        private final Set<String> rootIds = new LinkedHashSet<>();

        /** 子部件ID -> 父部件ID 的衍生关系（按发现顺序保存，保证子节点排列稳定） */
        private final Map<String, String> parentLink = new LinkedHashMap<>();

        /** 部件ID -> 部件快照（上一代物料编码与规格值的当层数据来源） */
        private final Map<String, PbiFamilyPartSnapshot> snapshots = new LinkedHashMap<>();

        /** 部件ID -> 部件展示信息（发货量由回填器原地补充） */
        private final Map<String, PbiFamilyTreeNode> nodeInfo = new LinkedHashMap<>();

        /** 部件支系可见性的记忆化缓存 */
        private final Map<String, Boolean> visibleCache = new HashMap<>();

        /** 当前递归路径上的部件ID，防止异常数据成环导致死循环，回溯时移除 */
        private final Set<String> pathGuard = new HashSet<>();
    }
}
