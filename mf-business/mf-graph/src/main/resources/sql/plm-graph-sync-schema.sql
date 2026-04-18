-- ============================================================
-- PLM Graph Sync 数据库表创建脚本
-- 版本: v1.0
-- 日期: 2026-04-18
-- 说明: 创建幂等表和操作日志表
-- 数据库: PostgreSQL (mf_plm)
-- ============================================================

-- 1. 创建幂等表 (sync_idempotent_log)
CREATE TABLE IF NOT EXISTS sync_idempotent_log (
    id              VARCHAR(64) PRIMARY KEY COMMENT '唯一ID（UUID）',
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    event_type      VARCHAR(20) NOT NULL COMMENT '事件类型：CREATE/UPDATE/DELETE',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED/FAILED',
    retry_count     BIGINT DEFAULT 0 COMMENT '处理版本/轮次',
    error_message   TEXT COMMENT '错误信息',
    create_by       VARCHAR(64) COMMENT '创建人',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64) COMMENT '更新人',
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 创建唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uq_sync_idempotent_log_event_id ON sync_idempotent_log(event_id);

-- 创建普通索引
CREATE INDEX IF NOT EXISTS idx_sync_idempotent_log_status ON sync_idempotent_log(status);
CREATE INDEX IF NOT EXISTS idx_sync_idempotent_log_create_time ON sync_idempotent_log(create_time);

-- 添加注释
COMMENT ON TABLE sync_idempotent_log IS '图同步幂等表';
COMMENT ON COLUMN sync_idempotent_log.id IS '唯一ID（UUID）';
COMMENT ON COLUMN sync_idempotent_log.event_id IS '事件唯一ID';
COMMENT ON COLUMN sync_idempotent_log.event_type IS '事件类型：CREATE/UPDATE/DELETE';
COMMENT ON COLUMN sync_idempotent_log.node_count IS '节点数量';
COMMENT ON COLUMN sync_idempotent_log.edge_count IS '边数量';
COMMENT ON COLUMN sync_idempotent_log.status IS '状态：PROCESSING/COMPLETED/FAILED';
COMMENT ON COLUMN sync_idempotent_log.retry_count IS '重试次数';
COMMENT ON COLUMN sync_idempotent_log.error_message IS '错误信息';

-- 2. 创建操作日志表 (sync_operation_log)
CREATE TABLE IF NOT EXISTS sync_operation_log (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    operation       VARCHAR(20) NOT NULL COMMENT '操作类型：PREPARE/PROCESS/COMPLETE/FAILED',
    node_count      BIGINT DEFAULT 0 COMMENT '节点数量',
    edge_count      BIGINT DEFAULT 0 COMMENT '边数量',
    start_time      TIMESTAMP NOT NULL COMMENT '开始时间',
    end_time        TIMESTAMP COMMENT '结束时间',
    duration_ms     BIGINT COMMENT '耗时(毫秒)',
    status          VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    error_message   TEXT COMMENT '错误信息',
    detail          JSONB COMMENT '详细信息',
    create_by       VARCHAR(64) COMMENT '创建人',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       VARCHAR(64) COMMENT '更新人',
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_sync_operation_log_event_id ON sync_operation_log(event_id);
CREATE INDEX IF NOT EXISTS idx_sync_operation_log_status ON sync_operation_log(status);
CREATE INDEX IF NOT EXISTS idx_sync_operation_log_create_time ON sync_operation_log(create_time);

-- 添加注释
COMMENT ON TABLE sync_operation_log IS '图同步操作日志表';
COMMENT ON COLUMN sync_operation_log.id IS '唯一ID（自增）';
COMMENT ON COLUMN sync_operation_log.event_id IS '事件唯一ID';
COMMENT ON COLUMN sync_operation_log.operation IS '操作类型：PREPARE/PROCESS/COMPLETE/FAILED';
COMMENT ON COLUMN sync_operation_log.node_count IS '节点数量';
COMMENT ON COLUMN sync_operation_log.edge_count IS '边数量';
COMMENT ON COLUMN sync_operation_log.start_time IS '开始时间';
COMMENT ON COLUMN sync_operation_log.end_time IS '结束时间';
COMMENT ON COLUMN sync_operation_log.duration_ms IS '耗时(毫秒)';
COMMENT ON COLUMN sync_operation_log.status IS '状态：SUCCESS/FAILED';
COMMENT ON COLUMN sync_operation_log.error_message IS '错误信息';
COMMENT ON COLUMN sync_operation_log.detail IS '详细信息';

-- 3. 验证表创建
SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename IN ('sync_idempotent_log', 'sync_operation_log');