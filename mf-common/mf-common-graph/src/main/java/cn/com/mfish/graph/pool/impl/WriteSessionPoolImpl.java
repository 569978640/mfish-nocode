package cn.com.mfish.graph.pool.impl;

import cn.com.mfish.graph.config.NebulaGraphProperties;
import cn.com.mfish.graph.config.NebulaSessionPoolConfig;
import cn.com.mfish.graph.pool.*;

/**
 * NebulaGraph 写入会话池实现类
 * 写操作绑定主节点
 *
 * @author mfish
 * @date 2026-04-18
 */
public class WriteSessionPoolImpl extends MultiAddressSessionPool implements WriteSessionPool {
    public WriteSessionPoolImpl(NebulaGraphProperties properties,
                               NebulaSessionPoolConfig poolConfig,
                               AddressManager addressManager,
                               LoadBalancer loadBalancer) {
        super(properties, poolConfig, addressManager, loadBalancer);
    }
}