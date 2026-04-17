-- PLM模块数据库初始化脚本
-- 数据库: PostgreSQL
-- 数据库名: mf_plm
-- 创建时间: 2026-04-17
-- 说明: PLM产品库实体表，用于多数据库架构改造

-- 创建数据库（如果不存在）
-- CREATE DATABASE mf_plm;

-- 连接到 mf_plm 数据库后执行以下脚本

-- ============================================
-- 基础对象表 - 所有实体的父表
-- ============================================

-- 产品表
CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'Product',
    name VARCHAR(255),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE product IS '产品表';
COMMENT ON COLUMN product.id IS '产品ID';
COMMENT ON COLUMN product.type IS '类型';
COMMENT ON COLUMN product.name IS '产品名称';

-- ============================================
-- 文件夹表
-- ============================================
CREATE TABLE IF NOT EXISTS folder (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'Folder',
    name VARCHAR(255),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE folder IS '文件夹表';
COMMENT ON COLUMN folder.id IS '文件夹ID';
COMMENT ON COLUMN folder.name IS '文件夹名称';

-- ============================================
-- 部件主数据表
-- ============================================
CREATE TABLE IF NOT EXISTS part_master (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'PartMaster',
    number VARCHAR(100),
    name VARCHAR(255),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE part_master IS '部件主数据表';
COMMENT ON COLUMN part_master.id IS '部件主数据ID';
COMMENT ON COLUMN part_master.number IS '部件编号';
COMMENT ON COLUMN part_master.name IS '部件名称';

-- ============================================
-- 部件表
-- ============================================
CREATE TABLE IF NOT EXISTS part (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'Part',
    version VARCHAR(50),
    state VARCHAR(50),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE part IS '部件表';
COMMENT ON COLUMN part.id IS '部件ID';
COMMENT ON COLUMN part.version IS '版本';
COMMENT ON COLUMN part.state IS '状态';

-- ============================================
-- 文档主数据表
-- ============================================
CREATE TABLE IF NOT EXISTS document_master (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'DocumentMaster',
    number VARCHAR(100),
    name VARCHAR(255),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE document_master IS '文档主数据表';
COMMENT ON COLUMN document_master.id IS '文档主数据ID';
COMMENT ON COLUMN document_master.number IS '文档编号';
COMMENT ON COLUMN document_master.name IS '文档名称';

-- ============================================
-- 文档表
-- ============================================
CREATE TABLE IF NOT EXISTS document (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'Document',
    version VARCHAR(50),
    state VARCHAR(50),
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP
);

COMMENT ON TABLE document IS '文档表';
COMMENT ON COLUMN document.id IS '文档ID';
COMMENT ON COLUMN document.version IS '版本';
COMMENT ON COLUMN document.state IS '状态';

-- ============================================
-- 包含关系表 - 用于表达层级结构
-- ============================================
CREATE TABLE IF NOT EXISTS contains_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'ContainsLink',
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(100) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(100) NOT NULL,
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    properties TEXT
);

COMMENT ON TABLE contains_link IS '包含关系表';
COMMENT ON COLUMN contains_link.id IS '关系ID';
COMMENT ON COLUMN contains_link.from_id IS '起始节点ID';
COMMENT ON COLUMN contains_link.from_type IS '起始节点类型';
COMMENT ON COLUMN contains_link.to_id IS '目标节点ID';
COMMENT ON COLUMN contains_link.to_type IS '目标节点类型';
COMMENT ON COLUMN contains_link.properties IS '扩展属性JSON';

-- 创建包含关系的索引
CREATE INDEX IF NOT EXISTS idx_contains_link_from ON contains_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_contains_link_to ON contains_link(to_id, to_type);

-- ============================================
-- 部件版本迭代关系表
-- ============================================
CREATE TABLE IF NOT EXISTS part_version_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'PartVersionLink',
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(100) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(100) NOT NULL,
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    properties TEXT
);

COMMENT ON TABLE part_version_link IS '部件版本迭代关系表';
COMMENT ON COLUMN part_version_link.id IS '关系ID';
COMMENT ON COLUMN part_version_link.from_id IS '起始部件ID（上一个版本）';
COMMENT ON COLUMN part_version_link.from_type IS '起始节点类型';
COMMENT ON COLUMN part_version_link.to_id IS '目标部件ID（下一个版本）';
COMMENT ON COLUMN part_version_link.to_type IS '目标节点类型';

-- 创建部件版本关系的索引
CREATE INDEX IF NOT EXISTS idx_part_version_link_from ON part_version_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_part_version_link_to ON part_version_link(to_id, to_type);

-- ============================================
-- 文档版本迭代关系表
-- ============================================
CREATE TABLE IF NOT EXISTS doc_version_link (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(100) DEFAULT 'DocVersionLink',
    from_id VARCHAR(64) NOT NULL,
    from_type VARCHAR(100) NOT NULL,
    to_id VARCHAR(64) NOT NULL,
    to_type VARCHAR(100) NOT NULL,
    create_by VARCHAR(64),
    create_time TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP,
    properties TEXT
);

COMMENT ON TABLE doc_version_link IS '文档版本迭代关系表';
COMMENT ON COLUMN doc_version_link.id IS '关系ID';
COMMENT ON COLUMN doc_version_link.from_id IS '起始文档ID（上一个版本）';
COMMENT ON COLUMN doc_version_link.from_type IS '起始节点类型';
COMMENT ON COLUMN doc_version_link.to_id IS '目标文档ID（下一个版本）';
COMMENT ON COLUMN doc_version_link.to_type IS '目标节点类型';

-- 创建文档版本关系的索引
CREATE INDEX IF NOT EXISTS idx_doc_version_link_from ON doc_version_link(from_id, from_type);
CREATE INDEX IF NOT EXISTS idx_doc_version_link_to ON doc_version_link(to_id, to_type);

-- ============================================
-- 创建时间索引（用于审计查询）
-- ============================================
CREATE INDEX IF NOT EXISTS idx_product_create_time ON product(create_time);
CREATE INDEX IF NOT EXISTS idx_folder_create_time ON folder(create_time);
CREATE INDEX IF NOT EXISTS idx_part_master_create_time ON part_master(create_time);
CREATE INDEX IF NOT EXISTS idx_part_create_time ON part(create_time);
CREATE INDEX IF NOT EXISTS idx_document_master_create_time ON document_master(create_time);
CREATE INDEX IF NOT EXISTS idx_document_create_time ON document(create_time);

-- ============================================
-- 验证表创建
-- ============================================
SELECT '表创建完成' AS status;
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN (
    'product', 'folder', 'part_master', 'part',
    'document_master', 'document',
    'contains_link', 'part_version_link', 'doc_version_link'
) ORDER BY table_name;