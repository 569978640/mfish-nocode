package cn.com.mfish.graph.ops.impl;

import cn.com.mfish.graph.exception.BusinessException;
import cn.com.mfish.graph.id.VidGenerator;
import cn.com.mfish.graph.model.GraphNode;
import cn.com.mfish.graph.ops.NodeOperations;
import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.schema.SchemaUtils;
import com.vesoft.nebula.client.graph.data.ResultSet;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 节点操作实现类
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class NodeOperationsImpl implements NodeOperations {
    private final NebulaSessionPool sessionPool;
    private final VidGenerator vidGenerator;

    public NodeOperationsImpl(NebulaSessionPool sessionPool, VidGenerator vidGenerator) {
        this.sessionPool = sessionPool;
        this.vidGenerator = vidGenerator;
    }

    @Override
    public boolean insertNode(GraphNode node) {
        if (node.getId() == null) {
            node.setId(vidGenerator.generate(node.getNodeType(), node.getBizCode()));
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("INSERT VERTEX ");
        ngql.append(SchemaUtils.quote(node.getNodeType()));
        ngql.append("(");

        List<String> fields = getNodeFields(node);
        List<String> values = getNodeValues(node);

        ngql.append(String.join(", ", fields));
        ngql.append(") VALUES ");
        ngql.append(SchemaUtils.quote(node.getId()));
        ngql.append(":(");
        ngql.append(String.join(", ", values));
        ngql.append(")");

        try {
            return sessionPool.executeWrite(ngql.toString());
        } catch (Exception e) {
            log.error("插入节点失败: {}", node.getId(), e);
            throw new BusinessException("插入节点失败", e);
        }
    }

    @Override
    public boolean upsertNode(GraphNode node) {
        if (node.getId() == null) {
            node.setId(vidGenerator.generate(node.getNodeType(), node.getBizCode()));
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("UPSERT VERTEX ");
        ngql.append(SchemaUtils.quote(node.getId()));
        ngql.append(" ON ");
        ngql.append(SchemaUtils.quote(node.getNodeType()));
        ngql.append(" ");

        List<String> fields = getNodeFields(node);
        List<String> values = getNodeValues(node);

        ngql.append("SET ");
        List<String> setClauses = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            setClauses.add(fields.get(i) + " = " + values.get(i));
        }
        ngql.append(String.join(", ", setClauses));

        try {
            return sessionPool.executeWrite(ngql.toString());
        } catch (Exception e) {
            log.error("Upsert 节点失败: {}", node.getId(), e);
            throw new BusinessException("Upsert 节点失败", e);
        }
    }

    @Override
    public boolean updateNode(GraphNode node) {
        if (node.getId() == null) {
            throw new IllegalArgumentException("VID 不能为空");
        }

        StringBuilder ngql = new StringBuilder();
        ngql.append("UPDATE VERTEX ");
        ngql.append(SchemaUtils.quote(node.getId()));
        ngql.append(" ON ");
        ngql.append(SchemaUtils.quote(node.getNodeType()));
        ngql.append(" SET ");

        List<String> setClauses = new ArrayList<>();
        if (node.getName() != null) {
            setClauses.add("`name` = \"" + escapeValue(node.getName()) + "\"");
        }
        if (node.getDescription() != null) {
            setClauses.add("`description` = \"" + escapeValue(node.getDescription()) + "\"");
        }
        if (node.getExtData() != null) {
            setClauses.add("`ext_data` = \"" + escapeValue(node.getExtData()) + "\"");
        }
        if (node.getUpdateBy() != null) {
            setClauses.add("`update_by` = \"" + escapeValue(node.getUpdateBy()) + "\"");
        }

        ngql.append(String.join(", ", setClauses));

        try {
            return sessionPool.executeWrite(ngql.toString());
        } catch (Exception e) {
            log.error("更新节点失败: {}", node.getId(), e);
            throw new BusinessException("更新节点失败", e);
        }
    }

    @Override
    public boolean deleteNode(String vid) {
        String ngql = "DELETE VERTEX " + SchemaUtils.quote(vid);
        try {
            return sessionPool.executeWrite(ngql);
        } catch (Exception e) {
            log.error("删除节点失败: {}", vid, e);
            throw new BusinessException("删除节点失败", e);
        }
    }

    @Override
    public boolean softDeleteNode(String vid, String deleteBy) {
        String ngql = "UPDATE VERTEX " + SchemaUtils.quote(vid) + " SET `deleted` = true, `update_by` = \"" + escapeValue(deleteBy) + "\"";
        try {
            return sessionPool.executeWrite(ngql);
        } catch (Exception e) {
            log.error("软删除节点失败: {}", vid, e);
            throw new BusinessException("软删除节点失败", e);
        }
    }

    @Override
    public GraphNode getNodeByVid(String vid) {
        String ngql = "MATCH (n) WHERE id(n) == " + SchemaUtils.quote(vid) + " RETURN n";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return convertToGraphNode(result, 0);
            }
            return null;
        } catch (Exception e) {
            log.error("查询节点失败: {}", vid, e);
            throw new BusinessException("查询节点失败", e);
        }
    }

    @Override
    public GraphNode getNodeByBizCode(String bizCode) {
        String ngql = "MATCH (n) WHERE n.`biz_code` == \"" + escapeValue(bizCode) + "\" RETURN n LIMIT 1";
        try {
            ResultSet result = sessionPool.executeQuery(ngql);
            if (result.isSucceeded() && result.rowsSize() > 0) {
                return convertToGraphNode(result, 0);
            }
            return null;
        } catch (Exception e) {
            log.error("根据业务编码查询节点失败: {}", bizCode, e);
            throw new BusinessException("根据业务编码查询节点失败", e);
        }
    }

    @Override
    public int batchInsertNodes(List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (GraphNode node : nodes) {
            try {
                if (insertNode(node)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量插入节点失败: {}", node.getId(), e);
            }
        }
        return successCount;
    }

    @Override
    public int batchUpdateNodes(List<GraphNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (GraphNode node : nodes) {
            try {
                if (updateNode(node)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量更新节点失败: {}", node.getId(), e);
            }
        }
        return successCount;
    }

    @Override
    public int batchDeleteNodes(List<String> vids) {
        if (vids == null || vids.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (String vid : vids) {
            try {
                if (deleteNode(vid)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("批量删除节点失败: {}", vid, e);
            }
        }
        return successCount;
    }

    private List<String> getNodeFields(GraphNode node) {
        List<String> fields = new ArrayList<>();
        fields.add("`biz_code`");
        fields.add("`name`");
        if (node.getDescription() != null) {
            fields.add("`description`");
        }
        if (node.getExtData() != null) {
            fields.add("`ext_data`");
        }
        fields.add("`create_by`");
        fields.add("`create_time`");
        if (node.getUpdateBy() != null) {
            fields.add("`update_by`");
        }
        return fields;
    }

    private List<String> getNodeValues(GraphNode node) {
        List<String> values = new ArrayList<>();
        values.add("\"" + escapeValue(node.getBizCode()) + "\"");
        values.add("\"" + escapeValue(node.getName()) + "\"");
        if (node.getDescription() != null) {
            values.add("\"" + escapeValue(node.getDescription()) + "\"");
        }
        if (node.getExtData() != null) {
            values.add("\"" + escapeValue(node.getExtData()) + "\"");
        }
        values.add("\"" + escapeValue(node.getCreateBy()) + "\"");
        values.add(String.valueOf(System.currentTimeMillis()));
        if (node.getUpdateBy() != null) {
            values.add("\"" + escapeValue(node.getUpdateBy()) + "\"");
        }
        return values;
    }

    private GraphNode convertToGraphNode(ResultSet result, int rowIndex) {
        GraphNode node = new GraphNode();
        return node;
    }

    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}