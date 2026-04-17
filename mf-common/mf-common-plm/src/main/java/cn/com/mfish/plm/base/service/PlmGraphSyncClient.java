package cn.com.mfish.plm.base.service;

/**
 * PLM图同步客户端接口
 * 抽象图同步逻辑，不同实现可以支持不同的图数据库
 *
 * @author mfish
 * @date 2026-04-17
 */
public interface PlmGraphSyncClient {

    /**
     * 发送图同步事件
     *
     * @param entity 实体对象
     * @param nodeType 节点类型
     * @param eventType 事件类型
     * @param <T> 实体类型
     */
    <T> void sendGraphSyncEvent(T entity, String nodeType, String eventType);

    /**
     * 批量发送图同步事件
     *
     * @param entities 实体列表
     * @param nodeType 节点类型
     * @param eventType 事件类型
     * @param <T> 实体类型
     */
    <T> void sendGraphSyncEventBatch(java.util.List<T> entities, String nodeType, String eventType);
}
