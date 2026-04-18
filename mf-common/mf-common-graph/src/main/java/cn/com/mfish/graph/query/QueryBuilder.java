package cn.com.mfish.graph.query;

import cn.com.mfish.graph.schema.SchemaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 查询构建器
 * 构建 nGQL 查询语句，支持参数化查询防注入
 *
 * @author mfish
 * @date 2026-04-18
 */
public class QueryBuilder {
    private final StringBuilder ngql;
    private final List<Object> params;
    private int paramIndex = 0;

    private QueryBuilder() {
        this.ngql = new StringBuilder();
        this.params = new ArrayList<>();
    }

    public static QueryBuilder match() {
        QueryBuilder builder = new QueryBuilder();
        builder.ngql.append("MATCH ");
        return builder;
    }

    public static QueryBuilder go() {
        QueryBuilder builder = new QueryBuilder();
        builder.ngql.append("GO ");
        return builder;
    }

    public static QueryBuilder find() {
        QueryBuilder builder = new QueryBuilder();
        builder.ngql.append("FIND ");
        return builder;
    }

    public QueryBuilder node(String alias, String nodeType) {
        ngql.append("(").append(alias).append(":").append(SchemaUtils.quote(nodeType)).append(")");
        return this;
    }

    public QueryBuilder node(String alias) {
        ngql.append("(").append(alias).append(")");
        return this;
    }

    public QueryBuilder edge(String alias, String edgeType) {
        ngql.append("-[").append(alias).append(":").append(SchemaUtils.quote(edgeType)).append("]->");
        return this;
    }

    public QueryBuilder edge(String alias) {
        ngql.append("-[").append(alias).append("]->");
        return this;
    }

    public QueryBuilder reverseEdge(String alias, String edgeType) {
        ngql.append("<-[").append(alias).append(":").append(SchemaUtils.quote(edgeType)).append("]-");
        return this;
    }

    public QueryBuilder reverseEdge(String alias) {
        ngql.append("<-[").append(alias).append("]-");
        return this;
    }

    public QueryBuilder where(String field, QueryCondition.Operator operator, Object value) {
        ngql.append(" WHERE ");
        ngql.append(SchemaUtils.quote(field));
        ngql.append(" ").append(operator.getSymbol()).append(" ");
        if (value instanceof String) {
            ngql.append("\"").append(escapeValue((String) value)).append("\"");
        } else if (operator == QueryCondition.Operator.IN) {
            ngql.append(value);
        } else {
            ngql.append(value);
        }
        return this;
    }

    public QueryBuilder where(QueryCondition condition) {
        ngql.append(" WHERE ");
        ngql.append(SchemaUtils.quote(condition.getField()));
        ngql.append(" ").append(condition.getOperator().getSymbol()).append(" ");
        appendValue(condition.getValue(), condition.getOperator());
        ngql.append(" ");
        ngql.append(condition.getLogical());
        return this;
    }

    public QueryBuilder and(QueryCondition condition) {
        ngql.append(" AND ");
        ngql.append(SchemaUtils.quote(condition.getField()));
        ngql.append(" ").append(condition.getOperator().getSymbol()).append(" ");
        appendValue(condition.getValue(), condition.getOperator());
        return this;
    }

    public QueryBuilder or(QueryCondition condition) {
        ngql.append(" OR ");
        ngql.append(SchemaUtils.quote(condition.getField()));
        ngql.append(" ").append(condition.getOperator().getSymbol()).append(" ");
        appendValue(condition.getValue(), condition.getOperator());
        return this;
    }

    public QueryBuilder where(String condition) {
        ngql.append(" WHERE ").append(condition);
        return this;
    }

    public QueryBuilder returnDistinct(String... fields) {
        ngql.append(" RETURN DISTINCT ");
        ngql.append(buildReturnFields(fields));
        return this;
    }

    public QueryBuilder returns(String... fields) {
        ngql.append(" RETURN ");
        ngql.append(buildReturnFields(fields));
        return this;
    }

    public QueryBuilder returnsAll() {
        ngql.append(" RETURN *");
        return this;
    }

    public QueryBuilder limit(int limit) {
        ngql.append(" LIMIT ").append(limit);
        return this;
    }

    public QueryBuilder offset(int offset) {
        ngql.append(" OFFSET ").append(offset);
        return this;
    }

    public QueryBuilder orderBy(String field, boolean ascending) {
        ngql.append(" ORDER BY ").append(SchemaUtils.quote(field));
        if (!ascending) {
            ngql.append(" DESC");
        }
        return this;
    }

    public QueryBuilder groupBy(String... fields) {
        ngql.append(" GROUP BY ");
        ngql.append(java.util.Arrays.stream(fields).map(SchemaUtils::quote).collect(Collectors.joining(", ")));
        return this;
    }

    public QueryBuilder yield(String expression) {
        ngql.append(" YIELD ").append(expression);
        return this;
    }

    public String build() {
        return ngql.toString();
    }

    public List<Object> getParams() {
        return params;
    }

    private String buildReturnFields(String... fields) {
        return java.util.Arrays.stream(fields).map(f -> {
            if (f.contains(".")) {
                return f;
            }
            return SchemaUtils.quote(f);
        }).collect(Collectors.joining(", "));
    }

    private void appendValue(Object value, QueryCondition.Operator operator) {
        if (value instanceof String) {
            if (operator == QueryCondition.Operator.IN) {
                ngql.append(value);
            } else {
                ngql.append("\"").append(escapeValue((String) value)).append("\"");
            }
        } else if (value == null) {
            ngql.append("NULL");
        } else {
            ngql.append(value);
        }
    }

    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}