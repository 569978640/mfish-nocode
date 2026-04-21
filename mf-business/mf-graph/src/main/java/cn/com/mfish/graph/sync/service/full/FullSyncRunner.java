package cn.com.mfish.graph.sync.service.full;

import cn.com.mfish.graph.sync.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 全量同步执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sync.full.enabled", havingValue = "true", matchIfMissing = false)
public class FullSyncRunner {
    private final SyncService syncService;

    /**
     * 执行全量同步
     */
    public void run() {
        log.info("========== 开始全量同步 ==========");
        long startTime = System.currentTimeMillis();
        try {
            syncService.fullSync();
            log.info("========== 全量同步完成，耗时: {} ms ==========", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("全量同步异常", e);
            throw e;
        }
    }
}
