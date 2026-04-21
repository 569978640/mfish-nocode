package cn.com.mfish.graph.sync.cdc;

import cn.com.mfish.graph.sync.model.CdcEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Debezium Embedded 启动器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "debezium.enabled", havingValue = "true", matchIfMissing = false)
public class DebeziumRunner {
    private final PgCdcConfig pgCdcConfig;
//    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.topic:plm-graph-sync}")
    private String topic;

    private DebeziumEngine<ChangeEvent<String, String>> engine;

    @PostConstruct
    public void start() {
        log.info("启动 Debezium Embedded...");

        Configuration config = Configuration.create()
            .with("name", "plm-cdc-connector")
            .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
            .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
            .with("offset.storage.file.filename", "/tmp/offsets.dat")
            .with("offset.flush.interval.ms", "1000")
            .with("offset.commit.mode", "ACCEPTED")
            .with("database.hostname", pgCdcConfig.getHost())
            .with("database.port", pgCdcConfig.getPort())
            .with("database.user", pgCdcConfig.getUsername())
            .with("database.password", pgCdcConfig.getPassword())
            .with("database.dbname", pgCdcConfig.getDatabase())
            .with("database.server.name", "plm-server")
            .with("plugin.name", "pgoutput")
            .with("slot.name", pgCdcConfig.getSlot())
            .with("publication.name", pgCdcConfig.getPublication())
            .with("table.include.list", String.join(",", pgCdcConfig.getTables()))
            .with("topic.prefix", "plm")
            .with("schema.include.list", "public")
            .with("key.converter", "org.apache.kafka.connect.json.JsonConverter")
            .with("key.converter.schemas.enable", "false")
            .with("value.converter", "org.apache.kafka.connect.json.JsonConverter")
            .with("value.converter.schemas.enable", "false")
            .build();

        engine = DebeziumEngine.create(Json.class)
            .using(config.asProperties())
            .notifying(this::handleChangeEvent)
            .using((success, message, error) -> {
                log.info("Debezium 引擎关闭: success={}, message={}", success, message);
            })
            .build();

        executor.execute(engine);
        log.info("Debezium Embedded 已启动");
    }

    @PreDestroy
    public void stop() {
        log.info("停止 Debezium Embedded...");
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                log.error("关闭 Debezium 引擎失败", e);
            }
        }
    }

    private void handleChangeEvent(ChangeEvent event) {
        try {
            if (event.value() instanceof org.apache.kafka.connect.source.SourceRecord record) {
                CdcEvent cdcEvent = convertToCdcEvent(record);
                if (cdcEvent != null) {
                    sendToRocketMQ(cdcEvent);
                }
            } else {
                log.warn("未知的 ChangeEvent 类型: {}", event.value().getClass());
            }
        } catch (Exception e) {
            log.error("处理 Debezium 记录失败", e);
        }
    }

    private CdcEvent convertToCdcEvent(org.apache.kafka.connect.source.SourceRecord record) {
        try {
            String topic = record.topic();
            String table = topic.substring(topic.lastIndexOf('.') + 1);

            Object valueObj = record.value();
            if (valueObj == null) {
                return null;
            }

            CdcEvent event = new CdcEvent();
            event.setTable(table);

            if (valueObj instanceof org.apache.kafka.connect.data.Struct valueStruct) {
                event.setOp(String.valueOf(valueStruct.get("op")));
                event.setTs(valueStruct.getInt64("ts_ms"));

                org.apache.kafka.connect.data.Struct sourceStruct = valueStruct.getStruct("source");
                if (sourceStruct != null) {
                    event.setTable(sourceStruct.getString("table"));
                }

                if (valueStruct.getStruct("before") != null) {
                    event.setBefore(structToMap(valueStruct.getStruct("before")));
                }
                if (valueStruct.getStruct("after") != null) {
                    event.setAfter(structToMap(valueStruct.getStruct("after")));
                }
            } else {
                String jsonStr = objectMapper.writeValueAsString(valueObj);
                CdcEvent parsed = objectMapper.readValue(jsonStr, CdcEvent.class);
                return parsed;
            }

            return event;
        } catch (Exception e) {
            log.error("转换 CDC 记录失败: {}", record, e);
            return null;
        }
    }

    private java.util.Map<String, Object> structToMap(org.apache.kafka.connect.data.Struct struct) {
        if (struct == null) return null;
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        for (org.apache.kafka.connect.data.Field field : struct.schema().fields()) {
            map.put(field.name(), struct.get(field));
        }
        return map;
    }

    private void sendToRocketMQ(CdcEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            rocketMQTemplate.convertAndSend(topic, message);
            try {
//                rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
//                    @Override
//                    public void onSuccess(SendResult sendResult) {
//                        log.info("图同步事件发送成功,  result={}", message);
//                    }
//
//                    @Override
//                    public void onException(Throwable e) {
//                        log.error("图同步事件发送失败, message={}", message, e);
//                    }
//                });
            } catch (Exception e) {
                log.error("发送图同步事件异常, message={}", message, e);
            }
            log.debug("CDC 事件已发送到 RocketMQ: table={}, op={}", event.getTable(), event.getOp());
        } catch (Exception e) {
            log.error("发送消息到 RocketMQ 失败", e);
        }
    }
}
