package cn.com.mfish.graph.service;

import cn.com.mfish.graph.client.NebulaClient;
import cn.com.mfish.graph.model.node.GraphNode;
import cn.com.mfish.graph.model.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
public class GraphSyncService {

    @Autowired
    private NebulaClient nebulaClient;

    /**
     * 全量同步：清空图库，重新同步所有数据
     */
    public void fullSync() {
        log.info("开始全量同步...");
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 清空图数据库并重建Schema
            nebulaClient.clearAndRecreateSpace();

            // Step 2: TODO 同步所有节点
            // syncAllNodes();

            // Step 3: TODO 同步所有边
            // syncAllEdges();

            long endTime = System.currentTimeMillis();
            log.info("全量同步完成，耗时: {}ms", endTime - startTime);
        } catch (Exception e) {
            log.error("全量同步失败", e);
            throw new RuntimeException("全量同步失败", e);
        }
    }
}