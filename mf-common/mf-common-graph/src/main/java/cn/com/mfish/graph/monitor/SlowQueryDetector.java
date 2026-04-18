package cn.com.mfish.graph.monitor;

import cn.com.mfish.graph.pool.SessionPoolMonitor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢查询检测器
 * 检测执行时间超过阈值的查询
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class SlowQueryDetector {
    private final SessionPoolMonitor sessionPoolMonitor;
    private final long slowQueryThresholdMs;
    private final AtomicLong slowQueryCount = new AtomicLong(0);

    public SlowQueryDetector(SessionPoolMonitor sessionPoolMonitor, long slowQueryThresholdMs) {
        this.sessionPoolMonitor = sessionPoolMonitor;
        this.slowQueryThresholdMs = slowQueryThresholdMs;
    }

    public void recordQuery(String ngql, long executionTimeMs) {
        if (executionTimeMs > slowQueryThresholdMs) {
            slowQueryCount.incrementAndGet();
            log.warn("慢查询检测: ngql={}, executionTime={}ms, threshold={}ms",
                truncateNgql(ngql), executionTimeMs, slowQueryThresholdMs);
        }
    }

    public long getSlowQueryCount() {
        return slowQueryCount.get();
    }

    public void resetCount() {
        slowQueryCount.set(0);
    }

    private String truncateNgql(String ngql) {
        if (ngql == null) {
            return "";
        }
        return ngql.length() > 100 ? ngql.substring(0, 100) + "..." : ngql;
    }
}