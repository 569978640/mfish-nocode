CREATE TABLE PBI_FeatureParameterDisplayConfig (
                                                   first_category    VARCHAR2(255),
                                                   param_group       VARCHAR2(255),
                                                   param             VARCHAR2(255),
                                                   display_name      VARCHAR2(255),
                                                   internal_name     VARCHAR2(255),
                                                   column_name       VARCHAR2(255),
                                                   param_type       VARCHAR2(255),
                                                   group_color       VARCHAR2(100),
                                                   param_color       VARCHAR2(100)
);

-- 添加注释（可选，推荐）
COMMENT ON TABLE PBI_FeatureParameterDisplayConfig IS 'PBI特征参数展示配置表';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.first_category IS '一级分类';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.param_group IS '分组';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.param IS '参数编码';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.display_name IS '展示名称';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.internal_name IS '内部名称';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.column_name IS '列名';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.group_color IS '分组颜色';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.param_color IS '参数颜色';
COMMENT ON COLUMN PBI_FeatureParameterDisplayConfig.param_type IS '参数类型';
