package cn.com.mfish.graph.pool;

/**
 * NebulaGraph 读取会话池接口
 * 读操作优先从节点，主从延迟超阈值自动切主
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface ReadSessionPool extends NebulaSessionPool {
}