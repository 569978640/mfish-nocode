package cn.com.mfish.graph.model;

import lombok.Data;

import java.util.Date;

/**
 * 图谱节点基础实体
 * 所有业务节点继承此类，包含公共字段和自动填充逻辑
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class GraphBaseEntity {
    /**
     * 节点唯一标识（VID）
     */
    private String id;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除（软删除标记）
     */
    private Boolean deleted = false;

    /**
     * 业务线（用于灰度/隔离）
     */
    private String businessLine;

    /**
     * 自动填充创建信息
     *
     * @param createBy 创建人
     */
    public void fillCreateInfo(String createBy) {
        this.createBy = createBy;
        this.createTime = new Date();
    }

    /**
     * 自动填充更新信息
     *
     * @param updateBy 更新人
     */
    public void fillUpdateInfo(String updateBy) {
        this.updateBy = updateBy;
        this.updateTime = new Date();
    }

    /**
     * 标记为已删除
     *
     * @param deleteBy 删除人
     */
    public void markDeleted(String deleteBy) {
        this.deleted = true;
        this.updateBy = deleteBy;
        this.updateTime = new Date();
    }

    /**
     * 恢复删除
     *
     * @param restoreBy 恢复人
     */
    public void markRestored(String restoreBy) {
        this.deleted = false;
        this.updateBy = restoreBy;
        this.updateTime = new Date();
    }
}