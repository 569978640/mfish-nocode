package cn.com.mfish.graph.softdelete;

/**
 * 软删除清理接口
 * 管理已删除数据的归档和清理
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface SoftDeleteCleaner {
    /**
     * 归档过期数据
     *
     * @param days 保留天数
     * @return 归档数量
     */
    int archiveExpiredData(int days);

    /**
     * 清理已归档数据
     *
     * @return 清理数量
     */
    int cleanupArchivedData();

    /**
     * 恢复软删除数据
     *
     * @param vid VID
     * @return true=恢复成功
     */
    boolean restoreData(String vid);

    /**
     * 检查恢复可行性
     *
     * @param vid VID
     * @return true=可恢复
     */
    boolean validateRestoreFeasibility(String vid);

    /**
     * 归档存储位置
     */
    enum ArchiveStorage {
        OFFLINE_NEBULA,
        MINIO,
        HDFS
    }
}