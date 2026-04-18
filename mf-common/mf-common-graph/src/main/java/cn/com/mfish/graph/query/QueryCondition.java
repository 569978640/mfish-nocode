package cn.com.mfish.graph.query;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询条件
 * 用于构建参数化查询
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class QueryCondition {
    private String field;
    private Object value;
    private Operator operator;
    private String logical;

    public QueryCondition(String field, Object value, Operator operator) {
        this.field = field;
        this.value = value;
        this.operator = operator;
        this.logical = "AND";
    }

    public enum Operator {
        EQ("=="),
        NE("!="),
        GT(">"),
        GTE(">="),
        LT("<"),
        LTE("<="),
        IN("IN"),
        CONTAINS("CONTAINS"),
        STARTS_WITH("STARTS WITH"),
        ENDS_WITH("ENDS WITH"),
        REGEXP("REGEXP");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static QueryCondition eq(String field, Object value) {
        return new QueryCondition(field, value, Operator.EQ);
    }

    public static QueryCondition ne(String field, Object value) {
        return new QueryCondition(field, value, Operator.NE);
    }

    public static QueryCondition gt(String field, Object value) {
        return new QueryCondition(field, value, Operator.GT);
    }

    public static QueryCondition gte(String field, Object value) {
        return new QueryCondition(field, value, Operator.GTE);
    }

    public static QueryCondition lt(String field, Object value) {
        return new QueryCondition(field, value, Operator.LT);
    }

    public static QueryCondition lte(String field, Object value) {
        return new QueryCondition(field, value, Operator.LTE);
    }

    public static QueryCondition in(String field, Object value) {
        return new QueryCondition(field, value, Operator.IN);
    }

    public static QueryCondition contains(String field, Object value) {
        return new QueryCondition(field, value, Operator.CONTAINS);
    }

    public static QueryCondition startsWith(String field, Object value) {
        return new QueryCondition(field, value, Operator.STARTS_WITH);
    }

    public static QueryCondition endsWith(String field, Object value) {
        return new QueryCondition(field, value, Operator.ENDS_WITH);
    }

    public static QueryCondition regexp(String field, Object value) {
        return new QueryCondition(field, value, Operator.REGEXP);
    }

    public QueryCondition or() {
        this.logical = "OR";
        return this;
    }
}