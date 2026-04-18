package cn.com.mfish.graph.monitor;

import cn.com.mfish.graph.pool.SessionPoolMonitor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * NebulaGraph 监控器
 * 提供指标采集、存储和告警能力
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class NebulaMonitor {
    private final SessionPoolMonitor sessionPoolMonitor;
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> metricHistory = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private MetricStorage metricStorage = MetricStorage.PROMETHEUS;
    private long metricCollectInterval = 60;

    public NebulaMonitor(SessionPoolMonitor sessionPoolMonitor) {
        this.sessionPoolMonitor = sessionPoolMonitor;
    }

    public void startMonitoring() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::collectMetrics, 0, metricCollectInterval, TimeUnit.SECONDS);
            log.info("NebulaGraph 监控已启动，采集间隔: {}s", metricCollectInterval);
        }
    }

    public void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            log.info("NebulaGraph 监控已停止");
        }
    }

    private void collectMetrics() {
        try {
            MetricsSnapshot snapshot = createSnapshot();
            storeMetrics(snapshot);
            checkAlerts(snapshot);
        } catch (Exception e) {
            log.error("采集指标异常", e);
        }
    }

    private MetricsSnapshot createSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setTimestamp(System.currentTimeMillis());
        snapshot.setIdleCount(sessionPoolMonitor.getIdleCount());
        snapshot.setActiveCount(sessionPoolMonitor.getActiveCount());
        snapshot.setWaitingTasks(sessionPoolMonitor.getWaitingTasks());
        snapshot.setAvgBorrowWaitTime(sessionPoolMonitor.getAvgBorrowWaitTime());
        snapshot.setConnectionCreateFailures(sessionPoolMonitor.getConnectionCreateFailures());
        snapshot.setHeartbeatFailures(sessionPoolMonitor.getHeartbeatFailures());
        snapshot.setConnectionReuseRate(sessionPoolMonitor.getConnectionReuseRate());
        snapshot.setBorrowTimeouts(sessionPoolMonitor.getBorrowTimeouts());
        return snapshot;
    }

    private void storeMetrics(MetricsSnapshot snapshot) {
        if (metricStorage == MetricStorage.PROMETHEUS) {
            exportToPrometheus(snapshot);
        }
    }

    private void exportToPrometheus(MetricsSnapshot snapshot) {
        log.debug("NebulaGraph 指标: idle={}, active={}, waiting={}, avgWaitTime={}ms, reuseRate={}, createFailures={}, heartbeatFailures={}",
            snapshot.getIdleCount(), snapshot.getActiveCount(), snapshot.getWaitingTasks(),
            snapshot.getAvgBorrowWaitTime(), snapshot.getConnectionReuseRate(),
            snapshot.getConnectionCreateFailures(), snapshot.getHeartbeatFailures());
    }

    private void checkAlerts(MetricsSnapshot snapshot) {
        for (AlertRule rule : alertRules.values()) {
            if (!rule.isEnabled()) {
                continue;
            }

            double value = getMetricValue(snapshot, rule.getMetricName());
            boolean triggered = false;

            switch (rule.getMetricName()) {
                case "connection.create.failures":
                    triggered = value > rule.getThreshold();
                    break;
                case "heartbeat.failures":
                    triggered = value > rule.getThreshold();
                    break;
                case "borrow.timeout":
                    triggered = value > rule.getThreshold();
                    break;
            }

            if (triggered) {
                sendAlert(rule, value);
            }
        }
    }

    private double getMetricValue(MetricsSnapshot snapshot, String metricName) {
        return switch (metricName) {
            case "idle.count" -> snapshot.getIdleCount();
            case "active.count" -> snapshot.getActiveCount();
            case "waiting.tasks" -> snapshot.getWaitingTasks();
            case "avg.borrow.wait.time" -> snapshot.getAvgBorrowWaitTime();
            case "connection.create.failures" -> snapshot.getConnectionCreateFailures();
            case "heartbeat.failures" -> snapshot.getHeartbeatFailures();
            case "connection.reuse.rate" -> snapshot.getConnectionReuseRate();
            case "borrow.timeout" -> snapshot.getBorrowTimeouts();
            default -> 0;
        };
    }

    private void sendAlert(AlertRule rule, double value) {
        log.warn("触发告警: ruleId={}, metric={}, value={}, threshold={}, level={}",
            rule.getRuleId(), rule.getMetricName(), value, rule.getThreshold(), rule.getLevel());
    }

    public void updateAlertRule(String ruleId, AlertRule rule) {
        alertRules.put(ruleId, rule);
        log.info("更新告警规则: ruleId={}", ruleId);
    }

    public void setMetricStorage(MetricStorage storage) {
        this.metricStorage = storage;
    }

    public void setMetricCollectInterval(long intervalMs) {
        this.metricCollectInterval = intervalMs / 1000;
    }

    public void integrateTracing(String traceId, Map<String, String> spanTags) {
        log.debug("集成全链路追踪: traceId={}, tags={}", traceId, spanTags);
    }

    /**
     * 指标存储介质
     */
    public enum MetricStorage {
        PROMETHEUS,
        INFLUXDB,
        ELASTICSEARCH
    }

    /**
     * 指标快照
     */
    @Data
    public static class MetricsSnapshot {
        private long timestamp;
        private int idleCount;
        private int activeCount;
        private int waitingTasks;
        private long avgBorrowWaitTime;
        private long connectionCreateFailures;
        private long heartbeatFailures;
        private double connectionReuseRate;
        private long borrowTimeouts;
        private long connectionCreateTime;
        private long connectionDestroyCount;
        private long warmupFailures;
        private double reuseRate;
    }
}