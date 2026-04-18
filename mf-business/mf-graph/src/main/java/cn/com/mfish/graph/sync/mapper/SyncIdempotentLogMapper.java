package cn.com.mfish.graph.sync.mapper;

import cn.com.mfish.graph.sync.entity.SyncIdempotentLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
 * @description: 幂等表
 * @author: mfish
 * @date: 2026-04-18
 * @version: V2.3.1
 */
public interface SyncIdempotentLogMapper extends BaseMapper<SyncIdempotentLog> {

    void insertOrUpdateIdempotent(@Param("id") String id,
                                   @Param("eventId") String eventId,
                                   @Param("eventType") String eventType,
                                   @Param("nodeCount") Long nodeCount,
                                   @Param("edgeCount") Long edgeCount,
                                   @Param("status") String status,
                                   @Param("retryCount") Long retryCount,
                                   @Param("updateTime") Date updateTime,
                                   @Param("createBy") String createBy,
                                   @Param("updateBy") String updateBy);

    SyncIdempotentLog selectForUpdate(@Param("eventId") String eventId);

}
