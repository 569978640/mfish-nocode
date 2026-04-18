-- ============================================================
-- NebulaGraph PLM 图数据库 Schema 创建脚本
-- 版本: v1.0
-- 日期: 2026-04-18
-- 说明: 创建 plm_graph 图空间，包含所有节点标签和边类型
-- ============================================================

-- 创建图空间
CREATE SPACE IF NOT EXISTS plm_graph
(
    partition_num = 100,
    replica_factor = 1,
    charset = utf8,
    collation = utf8_bin
);

-- 使用图空间
USE plm_graph;

-- ============================================================
-- 创建标签 (节点类型) - 存储 BaseEntity 公共属性
-- 节点类型: SsoOrg, Product, Folder, PartMaster, Part, DocumentMaster, Document
-- ============================================================

-- 组织节点
CREATE TAG IF NOT EXISTS SsoOrg(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 产品库节点
CREATE TAG IF NOT EXISTS Product(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 文件夹节点
CREATE TAG IF NOT EXISTS Folder(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 部件主数据节点
CREATE TAG IF NOT EXISTS PartMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 部件小版本节点
CREATE TAG IF NOT EXISTS Part(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 文档主数据节点
CREATE TAG IF NOT EXISTS DocumentMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 文档小版本节点
CREATE TAG IF NOT EXISTS Document(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- ============================================================
-- 创建边类型 - 存储 BaseLinkEntity 所有字段
-- 边类型: ContainsLink, PartVersionLink, DocVersionLink
-- ============================================================

-- 包含关系边
-- 说明: SsoOrg→Product, Product→Folder, Folder→Folder, Product→PartMaster, Product→DocumentMaster
CREATE EDGE IF NOT EXISTS ContainsLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);

-- 部件版本迭代关系边
-- 说明: PartMaster→Part
CREATE EDGE IF NOT EXISTS PartVersionLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);

-- 文档版本迭代关系边
-- 说明: DocumentMaster→Document
CREATE EDGE IF NOT EXISTS DocVersionLink(
    id string NOT NULL,
    type string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime,
    from_id string NOT NULL,
    from_type string NOT NULL,
    to_id string NOT NULL,
    to_type string NOT NULL,
    properties string
);

-- ============================================================
-- 创建索引 (用于高效查询)
-- ============================================================

-- 节点标签索引
CREATE INDEX IF NOT EXISTS idx_ssoorg_id ON SsoOrg(id);
CREATE INDEX IF NOT EXISTS idx_product_id ON Product(id);
CREATE INDEX IF NOT EXISTS idx_folder_id ON Folder(id);
CREATE INDEX IF NOT EXISTS idx_partmaster_id ON PartMaster(id);
CREATE INDEX IF NOT EXISTS idx_part_id ON Part(id);
CREATE INDEX IF NOT EXISTS idx_documentmaster_id ON DocumentMaster(id);
CREATE INDEX IF NOT EXISTS idx_document_id ON Document(id);

-- 边类型索引
CREATE INDEX IF NOT EXISTS idx_containslink_id ON ContainsLink(id);
CREATE INDEX IF NOT EXISTS idx_partversionlink_id ON PartVersionLink(id);
CREATE INDEX IF NOT EXISTS idx_docversionlink_id ON DocVersionLink(id);

-- ============================================================
-- 验证 Schema
-- ============================================================

-- 查看所有标签
SHOW TAGS;

-- 查看所有边类型
SHOW EDGES;

-- 查看标签详情
DESCRIBE TAG SsoOrg;
DESCRIBE TAG Product;
DESCRIBE TAG Folder;
DESCRIBE TAG PartMaster;
DESCRIBE TAG Part;
DESCRIBE TAG DocumentMaster;
DESCRIBE TAG Document;

-- 查看边类型详情
DESCRIBE EDGE ContainsLink;
DESCRIBE EDGE PartVersionLink;
DESCRIBE EDGE DocVersionLink;