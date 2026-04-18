package cn.com.mfish.graph.query;

import lombok.Data;

import java.util.List;

/**
 * 查询结果
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class QueryResult<T> {
    private List<T> data;
    private int total;
    private int pageNum;
    private int pageSize;
    private boolean hasMore;

    public QueryResult() {
    }

    public QueryResult(List<T> data, int total, int pageNum, int pageSize) {
        this.data = data;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.hasMore = (pageNum * pageSize) < total;
    }

    public static <T> QueryResult<T> of(List<T> data) {
        return new QueryResult<>(data, data.size(), 1, data.size());
    }

    public static <T> QueryResult<T> of(List<T> data, int total, int pageNum, int pageSize) {
        return new QueryResult<>(data, total, pageNum, pageSize);
    }
}