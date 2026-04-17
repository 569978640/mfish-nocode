package cn.com.mfish.plm.utils;

import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import cn.com.mfish.plm.base.bean.WFObject;
import cn.com.mfish.plm.base.bean.WFLink;
import cn.com.mfish.plm.base.bean.container.Product;
import cn.com.mfish.plm.base.bean.container.ContainsLink;
import cn.com.mfish.plm.base.bean.folder.Folder;
import cn.com.mfish.plm.base.bean.part.Part;
import cn.com.mfish.plm.base.bean.part.PartMaster;
import cn.com.mfish.plm.base.bean.part.PartVersionLink;
import cn.com.mfish.plm.base.bean.doc.Document;
import cn.com.mfish.plm.base.bean.doc.DocumentMaster;
import cn.com.mfish.plm.base.bean.doc.DocVersionLink;

import java.util.ArrayList;
import java.util.List;

/**
 * 图数据转换工具类
 * 将PLM实体转换为图节点和边
 *
 * @author mfish
 * @date 2026-04-17
 */
public class GraphUtils {

    /**
     * 将Product转换为GraphNode
     */
    public static GraphNode toGraphNode(Product product) {
        if (product == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(product.getId());
        node.setType("Product");
        node.setCreateBy(product.getCreateBy());
        node.setCreateTime(product.getCreateTime());
        node.setUpdateBy(product.getUpdateBy());
        node.setUpdateTime(product.getUpdateTime());
        return node;
    }

    /**
     * 将Folder转换为GraphNode
     */
    public static GraphNode toGraphNode(Folder folder) {
        if (folder == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(folder.getId());
        node.setType("Folder");
        node.setCreateBy(folder.getCreateBy());
        node.setCreateTime(folder.getCreateTime());
        node.setUpdateBy(folder.getUpdateBy());
        node.setUpdateTime(folder.getUpdateTime());
        return node;
    }

    /**
     * 将Part转换为GraphNode
     */
    public static GraphNode toGraphNode(Part part) {
        if (part == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(part.getId());
        node.setType("Part");
        node.setCreateBy(part.getCreateBy());
        node.setCreateTime(part.getCreateTime());
        node.setUpdateBy(part.getUpdateBy());
        node.setUpdateTime(part.getUpdateTime());
        return node;
    }

    /**
     * 将PartMaster转换为GraphNode
     */
    public static GraphNode toGraphNode(PartMaster partMaster) {
        if (partMaster == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(partMaster.getId());
        node.setType("PartMaster");
        node.setCreateBy(partMaster.getCreateBy());
        node.setCreateTime(partMaster.getCreateTime());
        node.setUpdateBy(partMaster.getUpdateBy());
        node.setUpdateTime(partMaster.getUpdateTime());
        return node;
    }

    /**
     * 将Document转换为GraphNode
     */
    public static GraphNode toGraphNode(Document document) {
        if (document == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(document.getId());
        node.setType("Document");
        node.setCreateBy(document.getCreateBy());
        node.setCreateTime(document.getCreateTime());
        node.setUpdateBy(document.getUpdateBy());
        node.setUpdateTime(document.getUpdateTime());
        return node;
    }

    /**
     * 将DocumentMaster转换为GraphNode
     */
    public static GraphNode toGraphNode(DocumentMaster docMaster) {
        if (docMaster == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        node.setId(docMaster.getId());
        node.setType("DocumentMaster");
        node.setCreateBy(docMaster.getCreateBy());
        node.setCreateTime(docMaster.getCreateTime());
        node.setUpdateBy(docMaster.getUpdateBy());
        node.setUpdateTime(docMaster.getUpdateTime());
        return node;
    }

    /**
     * 将ContainsLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(ContainsLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("ContainsLink");
        edge.setFromId(link.getFromId());
        edge.setFromType(link.getFromType());
        edge.setToId(link.getToId());
        edge.setToType(link.getToType());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 将PartVersionLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(PartVersionLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("PartVersionLink");
        edge.setFromId(link.getFromId());
        edge.setFromType(link.getFromType());
        edge.setToId(link.getToId());
        edge.setToType(link.getToType());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 将DocVersionLink转换为GraphEdge
     */
    public static GraphEdge toGraphEdge(DocVersionLink link) {
        if (link == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setId(link.getId());
        edge.setType("DocVersionLink");
        edge.setFromId(link.getFromId());
        edge.setFromType(link.getFromType());
        edge.setToId(link.getToId());
        edge.setToType(link.getToType());
        edge.setCreateBy(link.getCreateBy());
        edge.setCreateTime(link.getCreateTime());
        return edge;
    }

    /**
     * 批量转换节点列表
     */
    public static List<GraphNode> toNodeList(List<?> items) {
        List<GraphNode> nodes = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return nodes;
        }
        for (Object item : items) {
            GraphNode node = null;
            if (item instanceof Product) {
                node = toGraphNode((Product) item);
            } else if (item instanceof Folder) {
                node = toGraphNode((Folder) item);
            } else if (item instanceof Part) {
                node = toGraphNode((Part) item);
            } else if (item instanceof PartMaster) {
                node = toGraphNode((PartMaster) item);
            } else if (item instanceof Document) {
                node = toGraphNode((Document) item);
            } else if (item instanceof DocumentMaster) {
                node = toGraphNode((DocumentMaster) item);
            }
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }
}