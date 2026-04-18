package cn.com.mfish.graph.pool;

import com.vesoft.nebula.client.graph.data.ResultSet;

/**
 * NebulaGraph 会话池接口
 * 定义会话池的基本操作，包括借取、归还、执行查询等
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface NebulaSessionPool {
    /**
     * 借取 Session，超时则抛异常
     *
     * @return SessionWrapper 包装器
     */
    SessionWrapper borrowSession();

    /**
     * 归还 Session，包含状态校验和健康检查
     *
     * @param wrapper Session 包装器
     */
    void returnSession(SessionWrapper wrapper);

    /**
     * 执行查询语句
     *
     * @param ngql nGQL 查询语句
     * @return 查询结果
     */
    ResultSet executeQuery(String ngql);

    /**
     * 执行写入语句
     *
     * @param ngql nGQL 写入语句
     * @return true=成功，false=失败
     */
    boolean executeWrite(String ngql);

    /**
     * 销毁连接池，释放所有资源
     */
    void destroy();
}