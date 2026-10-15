package cn.com.mfish.bfces.pbi.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @description: PBI模块集合/字符串归一化工具。
 * 收敛缓存加载器、家谱读取器、发货量回填器中重复的"ID去重归一"、"Hash字段就绪判断"、
 * "同键多行保留首个非空值、冲突告警"三类通用逻辑。
 * @author: mfish
 * @date: 2026-10-15
 * @version: V1.0.0
 */
public final class PbiCollectionUtils {

    private PbiCollectionUtils() {
    }

    /**
     * 字符串列表归一化：过滤空白项、逐项trim、按出现顺序去重。
     *
     * @param rawValues 原始字符串列表
     * @return 归一化后的有序列表；入参为null时返回空列表
     */
    public static List<String> distinctTrimmed(List<String> rawValues) {
        List<String> result = new ArrayList<>();
        if (rawValues == null) {
            return result;
        }
        Set<String> exists = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (StringUtils.isNotBlank(rawValue) && exists.add(rawValue.trim())) {
                result.add(rawValue.trim());
            }
        }
        return result;
    }

    /**
     * 判断全部部件Hash是否都存在（非空Map）且包含指定字段（Hash空Map视为未就绪）。
     *
     * @param hashes         部件ID -> 属性Map
     * @param requiredFields 必需的Hash字段名集合
     * @return true表示全部就绪，可走无回源快路径
     */
    public static boolean allFieldsReady(Map<String, Map<String, String>> hashes,
                                         Collection<String> requiredFields) {
        for (Map<String, String> hash : hashes.values()) {
            if (hash == null || hash.isEmpty()) {
                return false;
            }
            for (String field : requiredFields) {
                if (!hash.containsKey(field)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 同键多行值归并：目标Map中该键无值时放入首个非空值；
     * 已存在非空值且新值不同时回调冲突处理器（调用方通常在此告警并沿用旧值）；
     * 新值为空白时忽略。
     *
     * @param target           归并目标Map
     * @param key              归并键
     * @param value            本次行值（已trim，可为null）
     * @param conflictHandler  值冲突回调（键、旧值、新值）
     */
    public static void putFirstNonBlank(Map<String, String> target, String key, String value,
                                        ConflictHandler conflictHandler) {
        String existing = target.get(key);
        if (existing == null) {
            if (value != null) {
                target.put(key, value);
            }
            return;
        }
        if (value != null && !existing.equals(value)) {
            conflictHandler.onConflict(key, existing, value);
        }
    }

    /**
     * 同键多值冲突回调。
     */
    @FunctionalInterface
    public interface ConflictHandler {

        /**
         * @param key      归并键
         * @param existing 已保留的首个值
         * @param incoming 本次冲突的新值
         */
        void onConflict(String key, String existing, String incoming);
    }
}
