package cn.com.mfish.graph.test;

import cn.com.mfish.graph.config.*;
import cn.com.mfish.graph.pool.AddressManager;
import cn.com.mfish.graph.pool.LoadBalancer;
import cn.com.mfish.graph.pool.impl.AddressManagerImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * NebulaGraph 会话池单元测试
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class SessionPoolTest {
    private AddressManager addressManager;
    private LoadBalancer loadBalancer;

    @Before
    public void setUp() {
        List<String> addresses = Arrays.asList("127.0.0.1:9669", "127.0.0.2:9669", "127.0.0.3:9669");
        LoadBalanceConfig config = new LoadBalanceConfig();
        addressManager = new AddressManagerImpl(addresses, config);
        loadBalancer = LoadBalancer.create(NebulaGraphProperties.LoadBalanceStrategy.FAILOVER);
    }

    @Test
    public void testAddressManager() {
        String available = addressManager.getAvailableAddress();
        log.info("获取可用地址: {}", available);
        assertNotNull(available);

        addressManager.recordFailure(available);
        log.info("地址失败次数: {} = {}", available, addressManager.getFailureCount(available));

        assertEquals(1, addressManager.getFailureCount(available));
    }

    @Test
    public void testLoadBalancer() {
        List<String> addresses = Arrays.asList("127.0.0.1:9669", "127.0.0.2:9669");

        String addr1 = loadBalancer.selectAddress(addresses);
        String addr2 = loadBalancer.selectAddress(addresses);

        log.info("负载均衡选择: {}, {}", addr1, addr2);
    }

    @Test
    public void testMultiAddressFailover() {
        List<String> addresses = Arrays.asList("127.0.0.1:9669", "127.0.0.2:9669");

        String addr = addressManager.getAvailableAddress();
        log.info("当前可用地址: {}", addr);

        addressManager.recordFailure(addr);
        addressManager.recordFailure(addr);
        addressManager.recordFailure(addr);

        assertFalse(addressManager.isAvailable(addr));

        String newAddr = addressManager.getAvailableAddress();
        log.info("故障后可用地址: {}", newAddr);
    }
}