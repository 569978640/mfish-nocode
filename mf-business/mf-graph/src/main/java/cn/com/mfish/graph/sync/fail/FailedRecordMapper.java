package cn.com.mfish.graph.sync.fail;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 失败记录 Mapper
 */
@Mapper
public interface FailedRecordMapper extends BaseMapper<FailedRecord> {
}
