package cn.com.mfish.graph.sync.cdc;

import cn.com.mfish.graph.sync.model.CdcEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Debezium Embedded 启动器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "debezium.enabled", havingValue = "true", matchIfMissing = false)
public class DebeziumRunner {
    private final PgCdcConfig pgCdcConfig;
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

        String tableList = String.join(",", pgCdcConfig.getTables());
        log.info("table.include.list = {}", tableList);

        Configuration config = Configuration.create()
                .with("name", "plm-cdc-connector")
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")

                // ====================== 核心：Redis 存储偏移量 ======================
                .with("offset.storage", "io.debezium.storage.redis.offset.RedisOffsetBackingStore")
                .with("offset.storage.redis.address", "192.168.111.103:6379")  // 你的 Redis 地址
                .with("offset.storage.redis.password", "redis")   // 没有密码就删掉这行
                .with("offset.storage.redis.database", "0")              // Redis 库号
                .with("offset.storage.redis.key", "debezium-offset")      // 必须指定 Redis key
                .with("offset.storage.redis.timeout.ms", 2000)            // 超时
                .with("offset.storage.redis.connection.max.idle", 10)     // 连接池
                .with("offset.flush.interval.ms", "1000")
                .with("offset.commit.mode", "PERIODIC")

                // ====================== 关键：只第一次全量，后续重启增量 ======================
                .with("snapshot.mode", "initial")

                // ====================== 以下全部是你原来的配置，完全不动 ======================
                .with("database.hostname", pgCdcConfig.getHost())
                .with("database.port", pgCdcConfig.getPort())
                .with("database.user", pgCdcConfig.getUsername())
                .with("database.password", pgCdcConfig.getPassword())
                .with("database.dbname", pgCdcConfig.getDatabase())
                .with("database.server.name", "plm-server")
                .with("heartbeat.interval.ms", "30000")
                .with("heartbeat.action.query", "SELECT 1")
                .with("plugin.name", "pgoutput")
                .with("slot.name", pgCdcConfig.getSlot())
                .with("publication.name", pgCdcConfig.getPublication())
                .with("table.include.list", tableList)
                .with("topic.prefix", "plm")
                .with("schema.include.list", "public")
                .with("key.converter", "org.apache.kafka.connect.json.JsonConverter")
                .with("key.converter.schemas.enable", "false")
                .with("value.converter", "org.apache.kafka.connect.json.JsonConverter")
                .with("value.converter.schemas.enable", "false")
                .with("signal.enabled", "true")
                .with("signaling.data-collection", "public.sync_failed_records")
                .with("poll.interval.ms", "100")
                .build();

        log.info("========== Debezium 配置项 ==========");
        config.asProperties().forEach((key, value) -> log.info("  {} = {}", key, value));
        log.info("======================================");

        engine = DebeziumEngine.create(Json.class)
                .using(config.asProperties())
                .notifying((DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>>) (records, committer) -> {
                    log.info("收到 Debezium 回调, 记录数={}", records.size());
                    for (ChangeEvent<String, String> record : records) {
                        try {
                            handleSingleEvent(record);
//                            committer.markProcessed(record);
                        } catch (Exception e) {
                            log.error("处理记录失败: key={}", record.key(), e);
                        }
                    }
                    committer.markBatchFinished();
                })
                .using((success, message, error) -> {
                    log.info("Debezium 引擎关闭: success={}, message={}, error={}", success, message, error);
                })
                .build();

        log.info("准备启动 Debezium 引擎...");
        log.info("配置项数量: {}", config.asProperties().size());
        executor.execute(engine);
        log.info("Debezium 引擎已提交到执行器");
    }

    private void handleSingleEvent(ChangeEvent<String, String> event) {
        try {
            String key = event.key();
            String value = event.value();
            log.info("收到 ChangeEvent, key={}, value长度={}", key, value != null ? value.length() : 0);

            if (value != null) {
                CdcEvent cdcEvent = parseJsonEvent(value);
                if (cdcEvent != null) {
                    sendToRocketMQ(cdcEvent);
                }
            }
        } catch (Exception e) {
            log.error("处理 Debezium 单条记录失败", e);
        }
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
            Object eventValue = event.value();
            log.info("收到 ChangeEvent, key 类型: {}, value 类型: {}",
                    event.key(), eventValue.getClass().getName());

            if (eventValue instanceof byte[] bytes) {
                String jsonStr = new String(bytes);
                log.info("收到字节数组事件, length={}, 内容预览={}",
                        bytes.length, jsonStr.substring(0, Math.min(200, jsonStr.length())));
                CdcEvent cdcEvent = parseJsonEvent(jsonStr);
                if (cdcEvent != null) {
                    sendToRocketMQ(cdcEvent);
                }
            } else if (eventValue instanceof String jsonStr) {
                log.info("收到字符串事件, length={}, 内容预览={}",
                        jsonStr.length(), jsonStr.substring(0, Math.min(200, jsonStr.length())));
                CdcEvent cdcEvent = parseJsonEvent(jsonStr);
                if (cdcEvent != null) {
                    sendToRocketMQ(cdcEvent);
                }
            } else if (eventValue instanceof org.apache.kafka.connect.source.SourceRecord record) {
                log.info("收到 SourceRecord: topic={}, partition={}, offset={}",
                        record.topic(), record.sourcePartition(), record.sourceOffset());
                CdcEvent cdcEvent = convertToCdcEvent(record);
                if (cdcEvent != null) {
                    sendToRocketMQ(cdcEvent);
                }
            } else {
                log.warn("未知的 ChangeEvent 类型: {}, value={}", eventValue.getClass(), eventValue);
            }
        } catch (Exception e) {
            log.error("处理 Debezium 记录失败", e);
        }
    }

    private CdcEvent parseJsonEvent(String jsonStr) {
        try {
            log.debug("解析 JSON 事件: {}", jsonStr);
            CdcEvent event = objectMapper.readValue(jsonStr, CdcEvent.class);
            //赋值tableName
            if (event.getSource() != null && event.getSource().containsKey("table")) {
                event.setTable(event.getSource().get("table").toString());
            }
            log.info("解析 CDC 事件成功: table={}, op={}", event.getTable(), event.getOp());
            return event;
        } catch (Exception e) {
            log.error("解析 CDC JSON 事件失败: {}", jsonStr, e);
            return null;
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
            log.info("发送 CDC 事件到 RocketMQ: topic={}, table={}, op={}", topic, event.getTable(), event.getOp());
            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("图同步事件发送成功, messageId={}, table={}, op={}",
                            sendResult.getMsgId(), event.getTable(), event.getOp());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("图同步事件发送失败, table={}, op={}, message={}",
                            event.getTable(), event.getOp(), message, e);
                }
            });
            log.debug("CDC 事件已发送到 RocketMQ: table={}, op={}", event.getTable(), event.getOp());
        } catch (Exception e) {
            log.error("发送消息到 RocketMQ 失败", e);
        }
    }
}
