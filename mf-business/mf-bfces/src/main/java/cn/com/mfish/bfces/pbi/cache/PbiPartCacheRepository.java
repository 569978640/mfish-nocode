package cn.com.mfish.bfces.pbi.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.sql.Clob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @description: PBI部件Redis缓存仓储。
 * 缓存模型（部件数据只新增、不修改，视图列不定时增加）：
 * 1.{@value #PART_DETAIL_PREFIX}{IDA2A2}：Hash，field=视图列名（大写），value=列值字符串，永久缓存；
 * field存在即代表该列已同步，空值写空串，以区分"未同步"与"该部件此列本来为空"；
 * 2.{@value #CATEGORY_IDS_PREFIX}{分类}：逗号分隔的IDA2A2列表，短TTL，缓存"分类→部件ID"实时窄查询；
 * 3.{@value #NUMBER_ID_PREFIX}{部件编号}：IDA2A2，短TTL，缓存"编号→部件ID"实时窄查询。
 * 本类只做Redis读写，不做降级判断：Redis异常直接向上抛出，由Service层统一降级直查Oracle。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiPartCacheRepository {

    /** 部件完整属性Hash前缀：pbi:part:detail:{IDA2A2} */
    public static final String PART_DETAIL_PREFIX = "pbi:part:detail:";

    /** 分类到部件ID列表的索引前缀：pbi:part:ids:category:{productClassification} */
    public static final String CATEGORY_IDS_PREFIX = "pbi:part:ids:category:";

    /** 部件编号到部件ID的索引前缀：pbi:part:id:no:{wtPartNumber} */
    public static final String NUMBER_ID_PREFIX = "pbi:part:id:no:";

    /** 清空全部PBI部件缓存的SCAN匹配模式 */
    public static final String ALL_PART_KEY_PATTERN = "pbi:part:*";

    /** 部件ID列名（Hash自带的主键field） */
    public static final String PART_ID_COLUMN = "IDA2A2";

    /** 编号查无结果的空值标记（IDA2A2恒为正数，"-"不可能为真实ID） */
    private static final String NULL_ID_MARKER = "-";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 读取分类下的部件ID列表（短缓存）。
     *
     * @param productClassification 二级分类内部名称
     * @return null表示未缓存；空列表表示已缓存"该分类无部件"；否则为有序部件ID列表
     */
    public List<String> getCategoryIds(String productClassification) {
        String raw = stringRedisTemplate.opsForValue().get(buildCategoryIdsKey(productClassification));
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    /**
     * 写入分类部件ID列表并设置短TTL；ttlSeconds小于等于0时不写缓存（调用方走每次实时查库）。
     *
     * @param productClassification 二级分类内部名称
     * @param ids                   有序部件ID列表（空列表也写入，防缓存穿透）
     * @param ttlSeconds            过期秒数
     */
    public void putCategoryIds(String productClassification, List<String> ids, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        String value = ids == null ? "" : String.join(",", ids);
        stringRedisTemplate.opsForValue().set(buildCategoryIdsKey(productClassification), value,
                java.time.Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 读取部件编号对应的部件ID（短缓存）。
     *
     * @param wtPartNumber 部件编号
     * @return null表示未缓存；空串表示已缓存"编号查无部件"；否则为部件ID
     */
    public String getIdByNumber(String wtPartNumber) {
        String value = stringRedisTemplate.opsForValue().get(buildNumberIdKey(wtPartNumber));
        if (NULL_ID_MARKER.equals(value)) {
            return "";
        }
        return value;
    }

    /**
     * 写入编号到部件ID的索引并设置短TTL；ttlSeconds小于等于0时不写缓存。
     *
     * @param wtPartNumber 部件编号
     * @param partId       部件ID；为空时写入空值标记，防缓存穿透
     * @param ttlSeconds   过期秒数
     */
    public void putIdByNumber(String wtPartNumber, String partId, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        String value = StringUtils.isBlank(partId) ? NULL_ID_MARKER : partId.trim();
        stringRedisTemplate.opsForValue().set(buildNumberIdKey(wtPartNumber), value,
                java.time.Duration.ofSeconds(ttlSeconds));
    }

    /**
     * pipeline批量读取部件属性Hash，返回顺序与入参ID顺序一致。
     *
     * @param partIds 部件ID集合
     * @return 部件ID -> 属性Map（field全大写）；Hash不存在时对应value为空Map
     */
    public Map<String, Map<String, String>> getPartHashes(List<String> partIds) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (partIds == null || partIds.isEmpty()) {
            return result;
        }
        // 注意：pipeline回调入参是Lettuce动态代理（仅实现RedisConnection），不能强转为StringRedisConnection；
        // 统一使用SessionCallback高层API（opsForHash），key/field/value序列化由StringRedisTemplate自动完成
        List<Object> pipelined = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                for (String partId : partIds) {
                    operations.opsForHash().entries(buildPartDetailKey(partId));
                }
                return null;
            }
        });
        for (int i = 0; i < partIds.size() && i < pipelined.size(); i++) {
            Object item = pipelined.get(i);
            if (item instanceof Map<?, ?> map) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    fields.put(String.valueOf(entry.getKey()),
                            entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                }
                result.put(partIds.get(i), fields);
            } else {
                result.put(partIds.get(i), Collections.emptyMap());
            }
        }
        return result;
    }

    /**
     * 将全列查询行批量写入部件Hash（pipeline hMSet，永久缓存）。
     * 行中必须包含IDA2A2列；列名统一转大写，null值写空串作为"已同步"标记。
     *
     * @param rows 全列查询行（key为列名，大小写不敏感）
     */
    public void savePartRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                for (Map<String, Object> row : rows) {
                    String partId = readPartId(row);
                    if (StringUtils.isBlank(partId)) {
                        log.warn("部件行缺少IDA2A2主键列，已跳过缓存写入，行内容:{}", row);
                        continue;
                    }
                    operations.opsForHash().putAll(buildPartDetailKey(partId), normalizeRow(row));
                }
                return null;
            }
        });
    }

    /**
     * 批量为部件Hash追加部分field（视图新增列补齐用，pipeline hMSet，永久缓存）。
     *
     * @param fieldsByPartId 部件ID -> 待追加的列值（key为大写字段名，null值需由调用方先归一为空串）
     */
    public void savePartFields(Map<String, Map<String, String>> fieldsByPartId) {
        if (fieldsByPartId == null || fieldsByPartId.isEmpty()) {
            return;
        }
        stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                for (Map.Entry<String, Map<String, String>> entry : fieldsByPartId.entrySet()) {
                    if (StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        operations.opsForHash().putAll(buildPartDetailKey(entry.getKey()), entry.getValue());
                    }
                }
                return null;
            }
        });
    }

    /**
     * SCAN扫描并清空全部PBI部件相关缓存（部件Hash、分类ID索引、编号ID索引）。
     * 生产环境禁用KEYS命令，使用SCAN分批扫描后批量删除。
     *
     * @return 实际删除的key数量
     */
    public long clearAll() {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(ALL_PART_KEY_PATTERN).count(500).build();
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (keys.isEmpty()) {
            return 0L;
        }
        Long deleted = stringRedisTemplate.delete(keys);
        long count = deleted == null ? 0L : deleted;
        log.info("已清空PBI部件Redis缓存，共删除key[{}]个，下次查询将自动从Oracle重新加载", count);
        return count;
    }

    /**
     * 将查询行归一化为Hash field结构：列名统一大写，列值统一转字符串，null归一为空串。
     * Oracle CLOB等大字段读取文本内容；DATE/NUMBER的toString结果作为文本展示值。
     *
     * @param row 查询行
     * @return 大写字段名 -> 字符串值
     */
    public static Map<String, String> normalizeRow(Map<String, Object> row) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            fields.put(entry.getKey().toUpperCase(), toCacheValue(entry.getValue()));
        }
        return fields;
    }

    /**
     * 读取行中的部件ID（IDA2A2），兼容列名大小写。
     *
     * @param row 查询行
     * @return 部件ID字符串；缺失时返回null
     */
    public static String readPartId(Map<String, Object> row) {
        Object value = row == null ? null : row.get(PART_ID_COLUMN);
        if (value == null) {
            // 兜底：部分驱动可能返回小写/混合大小写列名
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (PART_ID_COLUMN.equalsIgnoreCase(entry.getKey())) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value == null ? null : value.toString().trim();
    }

    /**
     * 将Oracle列值转为可写入Redis的字符串：Clob读取全文，null转空串，其余toString。
     *
     * @param value 原始列值
     * @return 字符串值
     */
    private static String toCacheValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Clob clob) {
            try {
                long length = clob.length();
                return length <= 0 ? "" : clob.getSubString(1, (int) Math.min(length, Integer.MAX_VALUE));
            } catch (Exception e) {
                log.warn("读取CLOB列值失败，已降级为空串", e);
                return "";
            }
        }
        return value.toString();
    }

    private String buildPartDetailKey(String partId) {
        return PART_DETAIL_PREFIX + partId;
    }

    private String buildCategoryIdsKey(String productClassification) {
        return CATEGORY_IDS_PREFIX + productClassification;
    }

    private String buildNumberIdKey(String wtPartNumber) {
        return NUMBER_ID_PREFIX + wtPartNumber;
    }
}
