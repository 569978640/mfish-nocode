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
        log.info("Redis 配置: address={}, database={}", pgCdcConfig.getRedisAddress(), pgCdcConfig.getRedisDatabase());
        log.info("Kafka 占位地址: {}", pgCdcConfig.getBootstrapServers());

        String tableList = String.join(",", pgCdcConfig.getTables());
        log.info("table.include.list = {}", tableList);

        Configuration config = Configuration.create()
                .with("name", "plm-cdc-connector")
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")

                // ====================== Kafka Connect 框架占位配置 ======================
                // Debezium Embedded 底层基于 Kafka Connect，WorkerConfig 初始化时该参数必填。
                // 实际数据通过 RocketMQ 转发，Kafka 不会真正消费。
                .with("bootstrap.servers", pgCdcConfig.getBootstrapServers())

                // ====================== 核心：Redis 存储偏移量 ======================
                .with("offset.storage", "io.debezium.storage.redis.offset.RedisOffsetBackingStore")
                .with("offset.storage.redis.address", pgCdcConfig.getRedisAddress())
                .with("offset.storage.redis.password", pgCdcConfig.getRedisPassword() != null ? pgCdcConfig.getRedisPassword() : "")
                .with("offset.storage.redis.database", pgCdcConfig.getRedisDatabase() != null ? pgCdcConfig.getRedisDatabase() : 0)
                .with("offset.storage.redis.key", "debezium-offset")
                .with("offset.storage.redis.timeout.ms", 10000)
                .with("offset.storage.redis.connection.max.idle", 10)
                .with("offset.storage.redis.connection.min.idle", 2)
                .with("offset.storage.redis.connection.max.total", 20)
                .with("offset.storage.redis.separator", ":")
                .with("offset.commit.mode", "PERIODIC")
                .with("offset.flush.interval.ms", 10000)

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
                            committer.markProcessed(record);
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

    private void sendToRocketMQ(CdcEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            log.info("发送 CDC 事件到 RocketMQ: topic={}, table={}, op={}", topic, event.getTable(), event.getOp());
            rocketMQTemplate.convertAndSend(topic, message);
//            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
//                @Override
//                public void onSuccess(SendResult sendResult) {
//                    log.info("图同步事件发送成功, messageId={}, table={}, op={}",
//                            sendResult.getMsgId(), event.getTable(), event.getOp());
//                }
//
//                @Override
//                public void onException(Throwable e) {
//                    log.error("图同步事件发送失败, table={}, op={}, message={}",
//                            event.getTable(), event.getOp(), message, e);
//                }
//            });
            log.debug("CDC 事件已发送到 RocketMQ: table={}, op={}", event.getTable(), event.getOp());
        } catch (Exception e) {
            log.error("发送消息到 RocketMQ 失败", e);
        }
    }
}
