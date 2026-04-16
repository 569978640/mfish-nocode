package cn.com.mfish.graph.common;

/**
 * 图数据库常量定义
 *
 * @author mfish
 * @date 2026-04-16
 */
public class GraphConstants {

    public static final String NODE_TYPE_SSO_ORG = "SsoOrg";
    public static final String NODE_TYPE_PRODUCT = "Product";
    public static final String NODE_TYPE_FOLDER = "Folder";
    public static final String NODE_TYPE_PART_MASTER = "PartMaster";
    public static final String NODE_TYPE_PART = "Part";
    public static final String NODE_TYPE_DOCUMENT_MASTER = "DocumentMaster";
    public static final String NODE_TYPE_DOCUMENT = "Document";

    public static final String EDGE_TYPE_CONTAINS_LINK = "ContainsLink";
    public static final String EDGE_TYPE_PART_VERSION_LINK = "PartVersionLink";
    public static final String EDGE_TYPE_DOC_VERSION_LINK = "DocVersionLink";

    public static final String GRAPH_SPACE_NAME = "plm_graph";

    public static final int DEFAULT_QUERY_DEPTH = 5;
    public static final int DEFAULT_TIMEOUT = 3000;
    public static final int DEFAULT_POOL_SIZE = 10;
}
