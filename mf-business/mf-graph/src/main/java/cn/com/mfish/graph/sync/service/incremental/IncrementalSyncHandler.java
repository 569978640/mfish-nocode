package cn.com.mfish.graph.sync.service.incremental;

import cn.com.mfish.graph.sync.model.*;
import cn.com.mfish.graph.sync.service.SyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 增量同步处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncrementalSyncHandler {
    private final SyncService syncService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理 RocketMQ 消息
     */
    public void handleMessage(String message) {
        try {
            CdcEvent event = parseMessage(message);
            if (event == null) {
                log.warn("解析消息失败或消息为空");
                return;
            }
            log.debug("处理CDC事件: table={}, op={}", event.getTable(), event.getOp());
            syncService.handleCdcEvent(event);
        } catch (Exception e) {
            log.error("处理增量同步消息失败: {}", message, e);
        }
    }

    private CdcEvent parseMessage(String message) {
        try {
            return objectMapper.readValue(message, CdcEvent.class);
        } catch (Exception e) {
            log.error("解析CDC消息异常: {}", message, e);
            return null;
        }
    }
}
