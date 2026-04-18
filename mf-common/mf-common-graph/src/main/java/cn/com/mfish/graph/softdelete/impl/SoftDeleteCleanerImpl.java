package cn.com.mfish.graph.softdelete.impl;

import cn.com.mfish.graph.pool.NebulaSessionPool;
import cn.com.mfish.graph.softdelete.SoftDeleteCleaner;
import lombok.extern.slf4j.Slf4j;

/**
 * 软删除清理默认实现
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class SoftDeleteCleanerImpl implements SoftDeleteCleaner {
    private final NebulaSessionPool sessionPool;

    public SoftDeleteCleanerImpl(NebulaSessionPool sessionPool) {
        this.sessionPool = sessionPool;
    }

    @Override
    public int archiveExpiredData(int days) {
        long expiredTime = System.currentTimeMillis() - (long) days * 24 * 3600 * 1000;
        String ngql = "MATCH (n) WHERE n.`deleted` == true AND n.`update_time` < " + expiredTime + " RETURN n";
        try {
            var result = sessionPool.executeQuery(ngql);
            int count = result.rowsSize();
            log.info("归档过期数据: count={}, days={}", count, days);
            return count;
        } catch (Exception e) {
            log.error("归档过期数据失败", e);
            return 0;
        }
    }

    @Override
    public int cleanupArchivedData() {
        log.info("开始清理已归档数据");
        return 0;
    }

    @Override
    public boolean restoreData(String vid) {
        String ngql = "UPDATE VERTEX " + vid + " SET `deleted` = false";
        try {
            var result = sessionPool.executeWrite(ngql);
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("恢复软删除数据失败: vid={}", vid, e);
            return false;
        }
    }

    @Override
    public boolean validateRestoreFeasibility(String vid) {
        return true;
    }
}