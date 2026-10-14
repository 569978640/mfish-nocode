package cn.com.mfish.bfces.pbi.support;

import cn.com.mfish.bfces.pbi.mapper.PbiPartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @description: PBI零件视图查询列选择器。
 * 负责视图真实列名的懒加载缓存，以及查询列的非法字符校验、忽略大小写去重与存在性校验；
 * 数据字典不可用时降级为"不做存在性校验"，避免影响主流程，同时保留失败后自动重试的能力。
 * @author: mfish
 * @date: 2026-10-14
 * @version: V1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PbiViewColumnSelector {

    /** PBI零件视图名 */
    public static final String PBI_PART_VIEW_NAME = "BFCES_PBI_PART_VIEW";

    /** 列名合法字符：仅允许字母、数字、下划线，防止异常配置导致SQL语法错误或SQL注入 */
    private static final String VALID_COLUMN_PATTERN = "^[A-Za-z0-9_]+$";

    private final PbiPartMapper pbiPartMapper;

    /** 视图真实列名缓存（统一转为大写）；视图结构稳定，懒加载一次；为null表示尚未加载或加载失败 */
    private volatile Set<String> viewColumnCache;

    /**
     * 基于候选列组装安全的查询列列表（保持入参顺序）：
     * 逐列执行空白过滤、非法字符校验、忽略大小写去重、视图存在性校验（降级模式下跳过）。
     *
     * @param candidateColumns 候选列名，调用方应保证固定列在前
     * @return 最终可拼入SQL的查询列列表；全部被过滤时返回空列表（调用方应据此终止查询）
     */
    public List<String> buildQueryColumns(List<String> candidateColumns) {
        Set<String> viewColumns = getViewColumns();
        List<String> columnList = new ArrayList<>();
        if (candidateColumns != null) {
            for (String candidate : candidateColumns) {
                addQueryColumn(columnList, viewColumns, candidate);
            }
        }
        return columnList;
    }

    /**
     * 获取PBI零件视图真实存在的列名（大写集合），懒加载并缓存。
     * 数据字典查询失败时返回null，调用方按"不做存在性校验"降级，避免影响主流程，同时保留失败后自动重试的能力。
     *
     * @return 视图列名大写集合；加载失败时为null
     */
    private Set<String> getViewColumns() {
        if (viewColumnCache != null) {
            return viewColumnCache;
        }
        synchronized (this) {
            if (viewColumnCache != null) {
                return viewColumnCache;
            }
            try {
                List<String> columns = pbiPartMapper.getViewColumns();
                if (columns == null || columns.isEmpty()) {
                    log.warn("未从数据字典查询到视图{}的列信息，列存在性校验降级为不校验", PBI_PART_VIEW_NAME);
                    return null;
                }
                Set<String> columnSet = new HashSet<>();
                for (String column : columns) {
                    if (StringUtils.isNotBlank(column)) {
                        columnSet.add(column.trim().toUpperCase());
                    }
                }
                viewColumnCache = columnSet;
                log.info("加载视图{}列信息成功，共{}列", PBI_PART_VIEW_NAME, columnSet.size());
                return viewColumnCache;
            } catch (Exception e) {
                log.error("查询视图{}列信息失败，列存在性校验降级为不校验", PBI_PART_VIEW_NAME, e);
                return null;
            }
        }
    }

    /**
     * 向查询列列表追加列名：过滤空白与非法字符，忽略大小写去重，并按视图真实列做存在性校验
     *
     * @param columnList  已组装的查询列列表
     * @param viewColumns 视图真实列名集合（大写），为null时不做存在性校验（降级模式）
     * @param columnName  待追加的列名
     */
    private void addQueryColumn(List<String> columnList, Set<String> viewColumns, String columnName) {
        if (StringUtils.isBlank(columnName)) {
            return;
        }
        String column = columnName.trim();
        if (!column.matches(VALID_COLUMN_PATTERN)) {
            log.warn("列名[{}]包含非法字符，已跳过", column);
            return;
        }
        // 忽略大小写去重
        for (String existingColumn : columnList) {
            if (existingColumn.equalsIgnoreCase(column)) {
                return;
            }
        }
        // 视图存在性校验（Oracle未加引号的标识符不区分大小写，统一按大写比对）
        if (viewColumns != null && !viewColumns.contains(column.toUpperCase())) {
            log.warn("列[{}]在视图{}中不存在，已跳过查询", column, PBI_PART_VIEW_NAME);
            return;
        }
        columnList.add(column);
    }
}
