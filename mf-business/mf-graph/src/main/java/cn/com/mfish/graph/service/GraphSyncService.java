package cn.com.mfish.graph.service;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.entity.*;
import cn.com.mfish.graph.mapper.*;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * 图同步服务
 * 负责全量同步和增量同步
 *
 * @author mfish
 * @date 2026-04-16
 */
@Slf4j
@Service
public class GraphSyncService {

    @Autowired
    private NebulaClient nebulaClient;

    @Autowired
    private SsoOrgMapper ssoOrgMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private FolderMapper folderMapper;
    @Autowired
    private PartMasterMapper partMasterMapper;
    @Autowired
    private PartMapper partMapper;
    @Autowired
    private DocumentMasterMapper documentMasterMapper;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private ContainsLinkMapper containsLinkMapper;
    @Autowired
    private PartVersionLinkMapper partVersionLinkMapper;
    @Autowired
    private DocVersionLinkMapper docVersionLinkMapper;

    /**
     * 全量同步：清空图库，重新同步所有数据
     */
    public void fullSync() {
        log.info("开始全量同步...");
        long startTime = System.currentTimeMillis();
        int totalNodes = 0;
        int totalEdges = 0;

        try {
            nebulaClient.clearAndRecreateSpace();
            log.info("图空间重建完成");

            totalNodes += syncAllNodes();
            totalEdges += syncAllEdges();

            long endTime = System.currentTimeMillis();
            log.info("全量同步完成，共同步{}个节点，{}条边，耗时: {}ms", totalNodes, totalEdges, endTime - startTime);
        } catch (Exception e) {
            log.error("全量同步失败", e);
            throw new RuntimeException("全量同步失败", e);
        }
    }

    /**
     * 同步所有节点
     *
     * @return 同步的节点数量
     */
    public int syncAllNodes() {
        int totalCount = 0;

        List<SsoOrg> orgs = ssoOrgMapper.selectList(null);
        if (orgs != null && !orgs.isEmpty()) {
            nebulaClient.batchInsertVertices("SsoOrg", convertNodes(orgs, "SsoOrg"));
            totalCount += orgs.size();
            log.info("同步SsoOrg节点: {}个", orgs.size());
        }

        List<Product> products = productMapper.selectList(null);
        if (products != null && !products.isEmpty()) {
            nebulaClient.batchInsertVertices("Product", convertNodes(products, "Product"));
            totalCount += products.size();
            log.info("同步Product节点: {}个", products.size());
        }

        List<Folder> folders = folderMapper.selectList(null);
        if (folders != null && !folders.isEmpty()) {
            nebulaClient.batchInsertVertices("Folder", convertNodes(folders, "Folder"));
            totalCount += folders.size();
            log.info("同步Folder节点: {}个", folders.size());
        }

        List<PartMaster> partMasters = partMasterMapper.selectList(null);
        if (partMasters != null && !partMasters.isEmpty()) {
            nebulaClient.batchInsertVertices("PartMaster", convertNodes(partMasters, "PartMaster"));
            totalCount += partMasters.size();
            log.info("同步PartMaster节点: {}个", partMasters.size());
        }

        List<Part> parts = partMapper.selectList(null);
        if (parts != null && !parts.isEmpty()) {
            nebulaClient.batchInsertVertices("Part", convertNodes(parts, "Part"));
            totalCount += parts.size();
            log.info("同步Part节点: {}个", parts.size());
        }

        List<DocumentMaster> docMasters = documentMasterMapper.selectList(null);
        if (docMasters != null && !docMasters.isEmpty()) {
            nebulaClient.batchInsertVertices("DocumentMaster", convertNodes(docMasters, "DocumentMaster"));
            totalCount += docMasters.size();
            log.info("同步DocumentMaster节点: {}个", docMasters.size());
        }

        List<Document> documents = documentMapper.selectList(null);
        if (documents != null && !documents.isEmpty()) {
            nebulaClient.batchInsertVertices("Document", convertNodes(documents, "Document"));
            totalCount += documents.size();
            log.info("同步Document节点: {}个", documents.size());
        }

        return totalCount;
    }

    /**
     * 同步所有边
     *
     * @return 同步的边数量
     */
    public int syncAllEdges() {
        int totalCount = 0;

        List<ContainsLink> containsLinks = containsLinkMapper.selectList(null);
        if (containsLinks != null && !containsLinks.isEmpty()) {
            nebulaClient.batchInsertEdges("ContainsLink", convertEdges(containsLinks, "ContainsLink"));
            totalCount += containsLinks.size();
            log.info("同步ContainsLink边: {}条", containsLinks.size());
        }

        List<PartVersionLink> partVersionLinks = partVersionLinkMapper.selectList(null);
        if (partVersionLinks != null && !partVersionLinks.isEmpty()) {
            nebulaClient.batchInsertEdges("PartVersionLink", convertEdges(partVersionLinks, "PartVersionLink"));
            totalCount += partVersionLinks.size();
            log.info("同步PartVersionLink边: {}条", partVersionLinks.size());
        }

        List<DocVersionLink> docVersionLinks = docVersionLinkMapper.selectList(null);
        if (docVersionLinks != null && !docVersionLinks.isEmpty()) {
            nebulaClient.batchInsertEdges("DocVersionLink", convertEdges(docVersionLinks, "DocVersionLink"));
            totalCount += docVersionLinks.size();
            log.info("同步DocVersionLink边: {}条", docVersionLinks.size());
        }

        return totalCount;
    }

    /**
     * 转换节点列表为图节点
     */
    private <T extends BaseEntity<?>> List<GraphNode> convertNodes(List<T> entities, String nodeType) {
        List<GraphNode> nodes = new ArrayList<>();
        if (entities == null) {
            return nodes;
        }
        for (T entity : entities) {
            GraphNode node = new GraphNode();
            node.setId(entity.getId());
            node.setType(nodeType);
            node.setCreateBy(entity.getCreateBy());
            node.setCreateTime(entity.getCreateTime());
            node.setUpdateBy(entity.getUpdateBy());
            node.setUpdateTime(entity.getUpdateTime());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 转换边列表为图边
     */
    private <T extends BaseTreeEntity<?>> List<GraphEdge> convertEdges(List<T> links, String edgeType) {
        List<GraphEdge> edges = new ArrayList<>();
        if (links == null) {
            return edges;
        }
        for (T link : links) {
            GraphEdge edge = new GraphEdge();
            edge.setId(link.getId());
            edge.setType(edgeType);
            edge.setCreateBy(link.getCreateBy());
            edge.setCreateTime(link.getCreateTime());
            edge.setUpdateBy(link.getUpdateBy());
            edge.setUpdateTime(link.getUpdateTime());
            edge.setFromId(link.getFromId());
            edge.setFromType(link.getFromType());
            edge.setToId(link.getToId());
            edge.setToType(link.getToType());
            edge.setProperties(link.getProperties());
            edges.add(edge);
        }
        return edges;
    }

    /**
     * 根据节点ID列表批量查询节点属性
     * 用于混合查询时关系库查询节点业务属性
     *
     * @param nodeIds 节点ID列表
     * @param nodeTypes 节点类型列表
     * @return 节点ID -> 节点属性映射
     */
    public Map<String, Map<String, Object>> batchQueryNodes(Set<String> nodeIds, Map<String, String> nodeTypes) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (Map.Entry<String, String> entry : nodeTypes.entrySet()) {
            String nodeId = entry.getKey();
            String nodeType = entry.getValue();

            Map<String, Object> attrs = switch (nodeType) {
                case "SsoOrg" -> querySsoOrgById(nodeId);
                case "Product" -> queryProductById(nodeId);
                case "Folder" -> queryFolderById(nodeId);
                case "PartMaster" -> queryPartMasterById(nodeId);
                case "Part" -> queryPartById(nodeId);
                case "DocumentMaster" -> queryDocumentMasterById(nodeId);
                case "Document" -> queryDocumentById(nodeId);
                default -> new HashMap<>();
            };

            if (attrs != null && !attrs.isEmpty()) {
                result.put(nodeId, attrs);
            }
        }

        return result;
    }

    private Map<String, Object> querySsoOrgById(String id) {
        SsoOrg org = ssoOrgMapper.selectById(id);
        if (org == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", org.getId());
        map.put("orgCode", org.getOrgCode());
        map.put("orgName", org.getOrgName());
        map.put("orgType", org.getOrgType());
        map.put("status", org.getStatus());
        return map;
    }

    private Map<String, Object> queryProductById(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("productCode", product.getProductCode());
        map.put("productName", product.getProductName());
        map.put("productType", product.getProductType());
        map.put("status", product.getStatus());
        return map;
    }

    private Map<String, Object> queryFolderById(String id) {
        Folder folder = folderMapper.selectById(id);
        if (folder == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", folder.getId());
        map.put("folderCode", folder.getFolderCode());
        map.put("folderName", folder.getFolderName());
        map.put("folderType", folder.getFolderType());
        map.put("status", folder.getStatus());
        return map;
    }

    private Map<String, Object> queryPartMasterById(String id) {
        PartMaster partMaster = partMasterMapper.selectById(id);
        if (partMaster == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", partMaster.getId());
        map.put("partMasterCode", partMaster.getPartMasterCode());
        map.put("partMasterName", partMaster.getPartMasterName());
        map.put("specification", partMaster.getSpecification());
        map.put("material", partMaster.getMaterial());
        map.put("status", partMaster.getStatus());
        return map;
    }

    private Map<String, Object> queryPartById(String id) {
        Part part = partMapper.selectById(id);
        if (part == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", part.getId());
        map.put("partCode", part.getPartCode());
        map.put("partName", part.getPartName());
        map.put("version", part.getVersion());
        map.put("status", part.getStatus());
        return map;
    }

    private Map<String, Object> queryDocumentMasterById(String id) {
        DocumentMaster docMaster = documentMasterMapper.selectById(id);
        if (docMaster == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", docMaster.getId());
        map.put("docMasterCode", docMaster.getDocMasterCode());
        map.put("docMasterName", docMaster.getDocMasterName());
        map.put("docType", docMaster.getDocType());
        map.put("status", docMaster.getStatus());
        return map;
    }

    private Map<String, Object> queryDocumentById(String id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", document.getId());
        map.put("docCode", document.getDocCode());
        map.put("docName", document.getDocName());
        map.put("version", document.getVersion());
        map.put("status", document.getStatus());
        return map;
    }
}