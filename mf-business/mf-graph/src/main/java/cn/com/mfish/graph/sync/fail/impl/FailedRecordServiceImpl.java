package cn.com.mfish.graph.sync.fail.impl;

import cn.com.mfish.graph.sync.fail.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 失败记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedRecordServiceImpl implements FailedRecordService {
    private final FailedRecordMapper failedRecordMapper;

    @Override
    public void saveFailedRecord(String tableName, String operationType, String payload, String errorMessage) {
        FailedRecord record = new FailedRecord();
        record.setTableName(tableName);
        record.setOperationType(operationType);
        record.setPayload(payload);
        record.setErrorMessage(errorMessage);
        record.setRetryCount(0);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        failedRecordMapper.insert(record);
        log.error("同步失败已记录: table={}, op={}, error={}", tableName, operationType, errorMessage);
    }

    @Override
    public void updateStatus(Long id, String status) {
        FailedRecord record = new FailedRecord();
        record.setId(id);
        record.setStatus(status);
        if ("PROCESSED".equals(status)) {
            record.setProcessTime(LocalDateTime.now());
        }
        failedRecordMapper.updateById(record);
    }

    @Override
    public List<FailedRecord> getPendingRecords(int limit) {
        LambdaQueryWrapper<FailedRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FailedRecord::getStatus, "PENDING")
               .orderByAsc(FailedRecord::getCreateTime)
               .last("LIMIT " + limit);
        return failedRecordMapper.selectList(wrapper);
    }
}
