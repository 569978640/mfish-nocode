-- PLM同步NebulaGraph失败记录表
-- 数据库：mf_plm

CREATE TABLE IF NOT EXISTS sync_failed_records (
    id              BIGSERIAL PRIMARY KEY,
    table_name      VARCHAR(100)  NOT NULL,
    operation_type  VARCHAR(20)   NOT NULL,
    payload         JSONB        NOT NULL,
    error_message   TEXT,
    retry_count     INT         DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    process_time    TIMESTAMP
);

COMMENT ON TABLE sync_failed_records IS 'PLM同步NebulaGraph失败记录表';
COMMENT ON COLUMN sync_failed_records.table_name IS '来源表名';
COMMENT ON COLUMN sync_failed_records.operation_type IS '操作类型：INSERT/UPDATE/DELETE';
COMMENT ON COLUMN sync_failed_records.payload IS '原始CDC消息JSON';
COMMENT ON COLUMN sync_failed_records.error_message IS '错误信息';
COMMENT ON COLUMN sync_failed_records.retry_count IS '重试次数';
COMMENT ON COLUMN sync_failed_records.status IS '处理状态：PENDING/PROCESSED';
COMMENT ON COLUMN sync_failed_records.create_time IS '创建时间';
COMMENT ON COLUMN sync_failed_records.process_time IS '处理时间';

-- 创建索引
CREATE INDEX idx_sync_failed_status ON sync_failed_records(status);
CREATE INDEX idx_sync_failed_table ON sync_failed_records(table_name);
CREATE INDEX idx_sync_failed_create_time ON sync_failed_records(create_time);
