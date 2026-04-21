package cn.com.mfish.graph.sync.fail;

import java.util.List;

/**
 * 失败记录服务接口
 */
public interface FailedRecordService {
    /**
     * 保存失败记录
     */
    void saveFailedRecord(String tableName, String operationType, String payload, String errorMessage);

    /**
     * 更新处理状态
     */
    void updateStatus(Long id, String status);

    /**
     * 获取待处理记录
     */
    List<FailedRecord> getPendingRecords(int limit);
}
