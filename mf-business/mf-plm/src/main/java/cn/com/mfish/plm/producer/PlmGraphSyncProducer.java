package cn.com.mfish.plm.producer;

import cn.com.mfish.graph.model.event.GraphSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PLM图同步消息生产者
 * 负责将PLM数据变更事件发送到RocketMQ
 *
 * @author mfish
 * @date 2026-04-17
 */
@Slf4j
@Component
public class PlmGraphSyncProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.topic:plm-graph-sync}")
    private String topic;

    /**
     * 发送图同步事件
     *
     * @param event 图同步事件
     */
    public void sendGraphSyncEvent(GraphSyncEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        if (event.getSource() == null) {
            event.setSource("mf-plm");
        }
        try {
            rocketMQTemplate.asyncSend(topic, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("图同步事件发送成功, eventId={}, result={}", event.getEventId(), sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("图同步事件发送失败, eventId={}", event.getEventId(), e);
                }
            });
        } catch (Exception e) {
            log.error("发送图同步事件异常, eventId={}", event.getEventId(), e);
        }
    }
}