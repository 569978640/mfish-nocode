-- PLM混合数据库 - PostgreSQL 建表SQL
-- 创建日期: 2026-04-16
-- 说明: 节点表 extends BaseEntity, 边表(Link) extends BaseTreeEntity

-- ============================================
-- 节点表 (Node Tables) - extends BaseEntity
-- BaseEntity: id, type, createBy, createTime, updateBy, updateTime
-- ============================================

-- 组织表 (SsoOrg)
CREATE TABLE IF NOT EXISTS sso_org (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'SsoOrg',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(255) NOT NULL,
    parent_id VARCHAR(64),
    org_level INTEGER DEFAULT 0,
    org_path VARCHAR(1000),
    sort INTEGER DEFAULT 0,
    status INTEGER DEFAULT 1,
    org_type VARCHAR(32),
    contact VARCHAR(128),
    phone VARCHAR(32),
    email VARCHAR(128),
    address VARCHAR(500),
    remark VARCHAR(1000)
);

COMMENT ON TABLE sso_org IS '组织表';
COMMENT ON COLUMN sso_org.id IS '组织ID';
COMMENT ON COLUMN sso_org.org_code IS '组织编码';
COMMENT ON COLUMN sso_org.org_name IS '组织名称';
COMMENT ON COLUMN sso_org.parent_id IS '父组织ID';
COMMENT ON COLUMN sso_org.org_level IS '组织层级';
COMMENT ON COLUMN sso_org.org_path IS '组织路径';
COMMENT ON COLUMN sso_org.sort IS '排序';
COMMENT ON COLUMN sso_org.status IS '状态: 1-正常, 0-禁用';
COMMENT ON COLUMN sso_org.org_type IS '组织类型';

-- 产品库表 (Product)
CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'Product',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    product_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    org_id VARCHAR(64),
    product_type VARCHAR(32),
    status INTEGER DEFAULT 1,
    sort INTEGER DEFAULT 0,
    remark VARCHAR(1000)
);

COMMENT ON TABLE product IS '产品库表';
COMMENT ON COLUMN product.id IS '产品ID';
COMMENT ON COLUMN product.product_code IS '产品编码';
COMMENT ON COLUMN product.product_name IS '产品名称';
COMMENT ON COLUMN product.org_id IS '所属组织ID';
COMMENT ON COLUMN product.product_type IS '产品类型';
COMMENT ON COLUMN product.status IS '状态: 1-正常, 0-禁用';

-- 文件夹表 (Folder)
CREATE TABLE IF NOT EXISTS folder (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'Folder',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    folder_code VARCHAR(64) NOT NULL,
    folder_name VARCHAR(255) NOT NULL,
    parent_id VARCHAR(64),
    folder_path VARCHAR(1000),
    folder_level INTEGER DEFAULT 0,
    sort INTEGER DEFAULT 0,
    folder_type VARCHAR(32),
    status INTEGER DEFAULT 1,
    remark VARCHAR(1000)
);

COMMENT ON TABLE folder IS '文件夹表';
COMMENT ON COLUMN folder.id IS '文件夹ID';
COMMENT ON COLUMN folder.folder_code IS '文件夹编码';
COMMENT ON COLUMN folder.folder_name IS '文件夹名称';
COMMENT ON COLUMN folder.parent_id IS '父文件夹ID';
COMMENT ON COLUMN folder.folder_path IS '文件夹路径';
COMMENT ON COLUMN folder.folder_level IS '文件夹层级';
COMMENT ON COLUMN folder.sort IS '排序';
COMMENT ON COLUMN folder.folder_type IS '文件夹类型';
COMMENT ON COLUMN folder.status IS '状态: 1-正常, 0-禁用';

-- 部件主数据表 (PartMaster)
CREATE TABLE IF NOT EXISTS part_master (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'PartMaster',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    part_master_code VARCHAR(64) NOT NULL,
    part_master_name VARCHAR(255) NOT NULL,
    product_id VARCHAR(64),
    specification VARCHAR(255),
    unit VARCHAR(32),
    material VARCHAR(128),
    status INTEGER DEFAULT 1,
    sort INTEGER DEFAULT 0,
    remark VARCHAR(1000)
);

COMMENT ON TABLE part_master IS '部件主数据表';
COMMENT ON COLUMN part_master.id IS '部件主数据ID';
COMMENT ON COLUMN part_master.part_master_code IS '部件主数据编码';
COMMENT ON COLUMN part_master.part_master_name IS '部件主数据名称';
COMMENT ON COLUMN part_master.product_id IS '所属产品ID';
COMMENT ON COLUMN part_master.specification IS '规格';
COMMENT ON COLUMN part_master.unit IS '单位';
COMMENT ON COLUMN part_master.material IS '材质';
COMMENT ON COLUMN part_master.status IS '状态: 1-正常, 0-禁用';

-- 部件表 (Part)
CREATE TABLE IF NOT EXISTS part (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'Part',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    part_master_id VARCHAR(64),
    part_code VARCHAR(64) NOT NULL,
    part_name VARCHAR(255) NOT NULL,
    version VARCHAR(32),
    status INTEGER DEFAULT 1,
    sort INTEGER DEFAULT 0,
    remark VARCHAR(1000)
);

COMMENT ON TABLE part IS '部件表';
COMMENT ON COLUMN part.id IS '部件ID';
COMMENT ON COLUMN part.part_master_id IS '部件主数据ID';
COMMENT ON COLUMN part.part_code IS '部件编码';
COMMENT ON COLUMN part.part_name IS '部件名称';
COMMENT ON COLUMN part.version IS '版本';
COMMENT ON COLUMN part.status IS '状态: 1-正常, 0-禁用';

-- 文档主数据表 (DocumentMaster)
CREATE TABLE IF NOT EXISTS document_master (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'DocumentMaster',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    doc_master_code VARCHAR(64) NOT NULL,
    doc_master_name VARCHAR(255) NOT NULL,
    product_id VARCHAR(64),
    doc_type VARCHAR(32),
    status INTEGER DEFAULT 1,
    sort INTEGER DEFAULT 0,
    remark VARCHAR(1000)
);

COMMENT ON TABLE document_master IS '文档主数据表';
COMMENT ON COLUMN document_master.id IS '文档主数据ID';
COMMENT ON COLUMN document_master.doc_master_code IS '文档主数据编码';
COMMENT ON COLUMN document_master.doc_master_name IS '文档主数据名称';
COMMENT ON COLUMN document_master.product_id IS '所属产品ID';
COMMENT ON COLUMN document_master.doc_type IS '文档类型';
COMMENT ON COLUMN document_master.status IS '状态: 1-正常, 0-禁用';

-- 文档表 (Document)
CREATE TABLE IF NOT EXISTS document (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'Document',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    doc_master_id VARCHAR(64),
    doc_code VARCHAR(64) NOT NULL,
    doc_name VARCHAR(255) NOT NULL,
    version VARCHAR(32),
    status INTEGER DEFAULT 1,
    sort INTEGER DEFAULT 0,
    remark VARCHAR(1000)
);

COMMENT ON TABLE document IS '文档表';
COMMENT ON COLUMN document.id IS '文档ID';
COMMENT ON COLUMN document.doc_master_id IS '文档主数据ID';
COMMENT ON COLUMN document.doc_code IS '文档编码';
COMMENT ON COLUMN document.doc_name IS '文档名称';
COMMENT ON COLUMN document.version IS '版本';
COMMENT ON COLUMN document.status IS '状态: 1-正常, 0-禁用';

-- ============================================
-- 边表 (Edge Tables / Link Tables) - extends BaseTreeEntity
-- BaseTreeEntity: id, type, createBy, createTime, updateBy, updateTime + parentId
-- Link表额外字段: fromId, fromType, toId, toType, properties(JSON)
-- ============================================

-- 包含关系表 (ContainsLink)
-- 用于: SsoOrg→Product, Product→Folder, Folder→Folder, Product→PartMaster, Product→DocumentMaster
CREATE TABLE IF NOT EXISTS contains_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'ContainsLink',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    parent_id VARCHAR(64),
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(32) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(32) NOT NULL,
    properties JSONB
);

COMMENT ON TABLE contains_link IS '包含关系表';
COMMENT ON COLUMN contains_link.id IS '关系ID';
COMMENT ON COLUMN contains_link.from_id IS '起始节点ID';
COMMENT ON COLUMN contains_link.from_type IS '起始节点类型';
COMMENT ON COLUMN contains_link.to_id IS '目标节点ID';
COMMENT ON COLUMN contains_link.to_type IS '目标节点类型';
COMMENT ON COLUMN contains_link.properties IS '关系业务属性(JSON)';

-- 部件版本迭代关系表 (PartVersionLink)
-- 用于: PartMaster→Part
CREATE TABLE IF NOT EXISTS part_version_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'PartVersionLink',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    parent_id VARCHAR(64),
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(32) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(32) NOT NULL,
    properties JSONB
);

COMMENT ON TABLE part_version_link IS '部件版本迭代关系表';
COMMENT ON COLUMN part_version_link.id IS '关系ID';
COMMENT ON COLUMN part_version_link.from_id IS '起始节点ID(PartMaster)';
COMMENT ON COLUMN part_version_link.from_type IS '起始节点类型';
COMMENT ON COLUMN part_version_link.to_id IS '目标节点ID(Part)';
COMMENT ON COLUMN part_version_link.to_type IS '目标节点类型';
COMMENT ON COLUMN part_version_link.properties IS '关系业务属性(JSON),如版本号等';

-- 文档版本迭代关系表 (DocVersionLink)
-- 用于: DocumentMaster→Document
CREATE TABLE IF NOT EXISTS doc_version_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) DEFAULT 'DocVersionLink',
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    parent_id VARCHAR(64),
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(32) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(32) NOT NULL,
    properties JSONB
);

COMMENT ON TABLE doc_version_link IS '文档版本迭代关系表';
COMMENT ON COLUMN doc_version_link.id IS '关系ID';
COMMENT ON COLUMN doc_version_link.from_id IS '起始节点ID(DocumentMaster)';
COMMENT ON COLUMN doc_version_link.from_type IS '起始节点类型';
COMMENT ON COLUMN doc_version_link.to_id IS '目标节点ID(Document)';
COMMENT ON COLUMN doc_version_link.to_type IS '目标节点类型';
COMMENT ON COLUMN doc_version_link.properties IS '关系业务属性(JSON),如版本号等';

-- ============================================
-- 索引创建
-- ============================================

-- sso_org 索引
CREATE INDEX IF NOT EXISTS idx_sso_org_parent_id ON sso_org(parent_id);
CREATE INDEX IF NOT EXISTS idx_sso_org_org_code ON sso_org(org_code);
CREATE INDEX IF NOT EXISTS idx_sso_org_status ON sso_org(status);

-- product 索引
CREATE INDEX IF NOT EXISTS idx_product_org_id ON product(org_id);
CREATE INDEX IF NOT EXISTS idx_product_product_code ON product(product_code);
CREATE INDEX IF NOT EXISTS idx_product_status ON product(status);

-- folder 索引
CREATE INDEX IF NOT EXISTS idx_folder_parent_id ON folder(parent_id);
CREATE INDEX IF NOT EXISTS idx_folder_folder_code ON folder(folder_code);
CREATE INDEX IF NOT EXISTS idx_folder_status ON folder(status);

-- part_master 索引
CREATE INDEX IF NOT EXISTS idx_part_master_product_id ON part_master(product_id);
CREATE INDEX IF NOT EXISTS idx_part_master_code ON part_master(part_master_code);
CREATE INDEX IF NOT EXISTS idx_part_master_status ON part_master(status);

-- part 索引
CREATE INDEX IF NOT EXISTS idx_part_master_id ON part(part_master_id);
CREATE INDEX IF NOT EXISTS idx_part_code ON part(part_code);
CREATE INDEX IF NOT EXISTS idx_part_status ON part(status);

-- document_master 索引
CREATE INDEX IF NOT EXISTS idx_doc_master_product_id ON document_master(product_id);
CREATE INDEX IF NOT EXISTS idx_doc_master_code ON document_master(doc_master_code);
CREATE INDEX IF NOT EXISTS idx_doc_master_status ON document_master(status);

-- document 索引
CREATE INDEX IF NOT EXISTS idx_document_master_id ON document(doc_master_id);
CREATE INDEX IF NOT EXISTS idx_document_code ON document(doc_code);
CREATE INDEX IF NOT EXISTS idx_document_status ON document(status);

-- contains_link 索引
CREATE INDEX IF NOT EXISTS idx_contains_link_from ON contains_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_contains_link_to ON contains_link(to_id, to_type);
CREATE INDEX IF NOT EXISTS idx_contains_link_parent_id ON contains_link(parent_id);

-- part_version_link 索引
CREATE INDEX IF NOT EXISTS idx_part_ver_link_from ON part_version_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_part_ver_link_to ON part_version_link(to_id, to_type);
CREATE INDEX IF NOT EXISTS idx_part_ver_link_parent_id ON part_version_link(parent_id);

-- doc_version_link 索引
CREATE INDEX IF NOT EXISTS idx_doc_ver_link_from ON doc_version_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_doc_ver_link_to ON doc_version_link(to_id, to_type);
CREATE INDEX IF NOT EXISTS idx_doc_ver_link_parent_id ON doc_version_link(parent_id);

-- ============================================
-- 结束
-- ============================================
