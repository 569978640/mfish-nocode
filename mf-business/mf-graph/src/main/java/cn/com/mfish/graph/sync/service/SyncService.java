package cn.com.mfish.graph.sync.service;

import cn.com.mfish.graph.sync.model.*;

/**
 * 同步服务接口
 */
public interface SyncService {
    /**
     * 执行全量同步
     */
    void fullSync();

    /**
     * 处理增量 CDC 事件
     */
    void handleCdcEvent(CdcEvent event);

    /**
     * 处理顶点事件
     */
    void handleVertexEvent(CdcEvent event);

    /**
     * 处理边事件
     */
    void handleEdgeEvent(CdcEvent event);
}
