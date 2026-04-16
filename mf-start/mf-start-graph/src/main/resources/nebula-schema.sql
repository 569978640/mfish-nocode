-- 创建图空间
CREATE SPACE IF NOT EXISTS plm_graph(
    partition_num = 100,
    replica_factor = 1,
    charset = utf8,
    collation = utf8_bin
);

-- 使用图空间
USE plm_graph;

-- 创建节点标签
CREATE TAG IF NOT EXISTS SsoOrg(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Product(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Folder(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS PartMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Part(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS DocumentMaster(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

CREATE TAG IF NOT EXISTS Document(
    id string NOT NULL,
    create_by string,
    create_time datetime,
    update_by string,
    update_time datetime
);

-- 创建边类型
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
