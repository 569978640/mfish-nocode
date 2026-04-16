package cn.com.mfish.graph.controller;

import cn.com.mfish.graph.service.GraphQueryService;
import cn.com.mfish.graph.service.GraphSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphController {

    @Autowired
    private GraphQueryService graphQueryService;

    @Autowired
    private GraphSyncService graphSyncService;

    /**
     * 路径查询
     */
    @PostMapping("/query/paths")
    public Map<String, Object> queryPaths(@RequestBody Map<String, Object> request) {
        String startId = (String) request.get("startId");
        return graphQueryService.queryProductTree(startId);
    }

    /**
     * 全量同步
     */
    @PostMapping("/sync/full")
    public void fullSync() {
        graphSyncService.fullSync();
    }
}