package cn.com.mfish.graph.sync.mapper;

import cn.com.mfish.graph.sync.fail.FailedRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 失败记录 Mapper
 */
@Mapper
public interface FailedRecordMapper extends BaseMapper<FailedRecord> {
}
