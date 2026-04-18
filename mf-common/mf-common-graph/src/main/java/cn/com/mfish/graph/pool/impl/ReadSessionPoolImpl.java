package cn.com.mfish.graph.pool.impl;

import cn.com.mfish.graph.config.NebulaGraphProperties;
import cn.com.mfish.graph.config.NebulaSessionPoolConfig;
import cn.com.mfish.graph.pool.*;

/**
 * NebulaGraph 读取会话池实现类
 * 读操作优先从节点，主从延迟超阈值自动切主
 *
 * @author mfish
 * @date 2026-04-18
 */
public class ReadSessionPoolImpl extends MultiAddressSessionPool implements ReadSessionPool {
    public ReadSessionPoolImpl(NebulaGraphProperties properties,
                              NebulaSessionPoolConfig poolConfig,
                              AddressManager addressManager,
                              LoadBalancer loadBalancer) {
        super(properties, poolConfig, addressManager, loadBalancer);
    }
}