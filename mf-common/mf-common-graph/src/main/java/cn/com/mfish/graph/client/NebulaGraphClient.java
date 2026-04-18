package cn.com.mfish.graph.client;

import cn.com.mfish.graph.config.LoadBalanceConfig;
import cn.com.mfish.graph.config.NebulaGraphProperties;
import cn.com.mfish.graph.config.NebulaSessionPoolConfig;
import cn.com.mfish.graph.pool.*;
import cn.com.mfish.graph.pool.impl.AddressManagerImpl;
import cn.com.mfish.graph.pool.impl.MultiAddressSessionPool;
import lombok.Getter;

/**
 * NebulaGraph 主客户端门面
 * 整合会话池、Schema 管理、CRUD 操作、查询模块
 *
 * @author mfish
 * @date 2026-04-18
 */
@Getter
public class NebulaGraphClient {
    private final NebulaSessionPool writePool;
    private final NebulaSessionPool readPool;

    public NebulaGraphClient(NebulaGraphProperties properties, NebulaSessionPoolConfig poolConfig) {
        LoadBalanceConfig loadBalanceConfig = new LoadBalanceConfig();
        loadBalanceConfig.setStrategy(properties.getLoadBalanceStrategy());

        AddressManager addressManager = new AddressManagerImpl(properties.getAddresses(), loadBalanceConfig);
        LoadBalancer loadBalancer = LoadBalancer.create(properties.getLoadBalanceStrategy());

        MultiAddressSessionPool writePoolImpl = new MultiAddressSessionPool(
            properties, poolConfig, addressManager, loadBalancer);
        writePoolImpl.init();
        this.writePool = writePoolImpl;

        MultiAddressSessionPool readPoolImpl = new MultiAddressSessionPool(
            properties, poolConfig, addressManager, loadBalancer);
        readPoolImpl.init();
        this.readPool = readPoolImpl;
    }

    public NebulaGraphClient(NebulaGraphProperties properties) {
        this(properties, new NebulaSessionPoolConfig());
    }

    public void destroy() {
        if (writePool != null) {
            writePool.destroy();
        }
        if (readPool != null) {
            readPool.destroy();
        }
    }
}