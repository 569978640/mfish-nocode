/*
 Navicat Premium Data Transfer

 Source Server         : 北康克隆机13.1.2
 Source Server Type    : Oracle
 Source Server Version : 190000 (Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production)
 Source Host           : 10.177.214.8:1521
 Source Schema         : WCADMIN

 Target Server Type    : Oracle
 Target Server Version : 190000 (Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production)
 File Encoding         : 65001

 Date: 20/10/2026 10:59:29
*/


-- ----------------------------
-- Table structure for PBI_FEATUREPARAMETERDISPLAYCONFIG
-- ----------------------------
DROP TABLE "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG";
CREATE TABLE "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG" (
                                                               "FIRST_CATEGORY" VARCHAR2(255 BYTE) VISIBLE,
                                                               "PARAM_GROUP" VARCHAR2(255 BYTE) VISIBLE,
                                                               "PARAM" VARCHAR2(255 BYTE) VISIBLE,
                                                               "DISPLAY_NAME" VARCHAR2(255 BYTE) VISIBLE,
                                                               "INTERNAL_NAME" VARCHAR2(255 BYTE) VISIBLE,
                                                               "COLUMN_NAME" VARCHAR2(255 BYTE) VISIBLE,
                                                               "PARAM_TYPE" VARCHAR2(255 BYTE) VISIBLE,
                                                               "ROLE" VARCHAR2(255 BYTE) VISIBLE,
                                                               "MEMBER" VARCHAR2(255 BYTE) VISIBLE,
                                                               "GROUP_COLOR" VARCHAR2(100 BYTE) VISIBLE,
                                                               "PARAM_COLOR" VARCHAR2(100 BYTE) VISIBLE,
                                                               "ALLOW_FOLD" VARCHAR2(255 BYTE) VISIBLE,
                                                               "FOLD_DISPLAY_VALUE" VARCHAR2(255 BYTE) VISIBLE
)
    LOGGING
NOCOMPRESS
PCTFREE 10
INITRANS 1
STORAGE (
  INITIAL 65536
  NEXT 1048576
  MINEXTENTS 1
  MAXEXTENTS 2147483645
  BUFFER_POOL DEFAULT
)
PARALLEL 1
NOCACHE
DISABLE ROW MOVEMENT
;
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."FIRST_CATEGORY" IS '一级分类';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."PARAM_GROUP" IS '分组';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."PARAM" IS '参数编码';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."DISPLAY_NAME" IS '展示名称';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."INTERNAL_NAME" IS '内部名称';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."COLUMN_NAME" IS '列名';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."PARAM_TYPE" IS '参数类型';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."ROLE" IS '角色';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."MEMBER" IS '成员';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."GROUP_COLOR" IS '分组颜色';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."PARAM_COLOR" IS '参数颜色';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."ALLOW_FOLD" IS '是否允许折叠';
COMMENT ON COLUMN "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG"."FOLD_DISPLAY_VALUE" IS '折叠显示值';
COMMENT ON TABLE "WCADMIN"."PBI_FEATUREPARAMETERDISPLAYCONFIG" IS 'PBI特征参数展示配置表';
