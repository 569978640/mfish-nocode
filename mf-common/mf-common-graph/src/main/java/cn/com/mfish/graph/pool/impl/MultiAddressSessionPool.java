package cn.com.mfish.graph.pool.impl;

import cn.com.mfish.graph.config.NebulaGraphProperties;
import cn.com.mfish.graph.config.NebulaSessionPoolConfig;
import cn.com.mfish.graph.exception.ConnectionException;
import cn.com.mfish.graph.pool.*;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.net.NebulaPool;
import com.vesoft.nebula.client.graph.net.Session;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NebulaGraph 多地址会话池实现类
 * 支持多地址高可用、负载均衡、连接池化管理
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class MultiAddressSessionPool implements NebulaSessionPool {
    private final NebulaGraphProperties properties;
    private final NebulaSessionPoolConfig poolConfig;
    private final AddressManager addressManager;
    private final LoadBalancer loadBalancer;
    private final ConcurrentLinkedQueue<SessionWrapper> idleSessions;
    private final SessionPoolMonitorImpl monitor;
    private final Semaphore borrowSemaphore;
    private final AtomicInteger activeCount;
    private volatile boolean destroyed = false;

    public MultiAddressSessionPool(NebulaGraphProperties properties,
                                   NebulaSessionPoolConfig poolConfig,
                                   AddressManager addressManager,
                                   LoadBalancer loadBalancer) {
        this.properties = properties;
        this.poolConfig = poolConfig;
        this.addressManager = addressManager;
        this.loadBalancer = loadBalancer;
        this.idleSessions = new ConcurrentLinkedQueue<>();
        this.monitor = new SessionPoolMonitorImpl();
        this.activeCount = new AtomicInteger(0);
        this.borrowSemaphore = new Semaphore(poolConfig.getMaxPoolSize());
    }

    @Override
    public SessionWrapper borrowSession() {
        if (destroyed) {
            throw new ConnectionException("连接池已销毁");
        }

        long startTime = System.currentTimeMillis();
        monitor.incrementWaitingTasks();

        try {
            if (!borrowSemaphore.tryAcquire(poolConfig.getBorrowTimeout(), TimeUnit.MILLISECONDS)) {
                monitor.recordBorrowTimeout();
                throw new ConnectionException("获取 Session 超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("获取 Session 被中断", e);
        } finally {
            monitor.decrementWaitingTasks();
        }

        long waitTime = System.currentTimeMillis() - startTime;
        monitor.recordBorrowWaitTime(waitTime);

        SessionWrapper wrapper = idleSessions.poll();
        if (wrapper != null && wrapper.getState() == SessionWrapper.SessionState.IDLE) {
            if (wrapper.isValid() && healthCheck(wrapper)) {
                wrapper.markActive();
                activeCount.incrementAndGet();
                monitor.decrementIdleCount();
                monitor.recordConnectionReuse();
                return wrapper;
            } else {
                destroySession(wrapper);
            }
        }

        String address = addressManager.getAvailableAddress();
        if (address == null) {
            borrowSemaphore.release();
            throw new ConnectionException("没有可用的 NebulaGraph 地址");
        }

        long createStartTime = System.currentTimeMillis();
        try {
            Session session = createSession(address);
            wrapper = SessionWrapper.createIdle(session, address);
            wrapper.markActive();

            long createTime = System.currentTimeMillis() - createStartTime;
            monitor.recordConnectionCreateTime(createTime);

            activeCount.incrementAndGet();
            return wrapper;
        } catch (Exception e) {
            addressManager.recordFailure(address);
            monitor.recordConnectionCreateFailure();
            borrowSemaphore.release();
            throw new ConnectionException("创建 Session 失败: " + address, e);
        }
    }

    @Override
    public void returnSession(SessionWrapper wrapper) {
        if (wrapper == null) {
            borrowSemaphore.release();
            return;
        }

        if (!wrapper.isValid() || !healthCheck(wrapper)) {
            wrapper.markInvalid();
            addressManager.recordFailure(wrapper.getAddress());
            destroySession(wrapper);
            activeCount.decrementAndGet();
            borrowSemaphore.release();
            return;
        }

        wrapper.markIdle();
        idleSessions.offer(wrapper);
        activeCount.decrementAndGet();
        monitor.incrementIdleCount();
    }

    @Override
    public ResultSet executeQuery(String ngql) {
        SessionWrapper wrapper = borrowSession();
        try {
            return wrapper.getSession().execute(ngql);
        } catch (Exception e) {
            log.error("执行查询失败: {}", ngql, e);
            throw new ConnectionException("执行查询失败", e);
        } finally {
            returnSession(wrapper);
        }
    }

    @Override
    public boolean executeWrite(String ngql) {
        SessionWrapper wrapper = borrowSession();
        try {
            ResultSet result = wrapper.getSession().execute(ngql);
            return result.isSucceeded();
        } catch (Exception e) {
            log.error("执行写入失败: {}", ngql, e);
            throw new ConnectionException("执行写入失败", e);
        } finally {
            returnSession(wrapper);
        }
    }

    @Override
    public void destroy() {
        destroyed = true;
        SessionWrapper wrapper;
        while ((wrapper = idleSessions.poll()) != null) {
            destroySession(wrapper);
        }
    }

    private Session createSession(String address) {
        String[] parts = address.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9669;

        NebulaPool pool = new NebulaPool();
        List<HostAddress> addresses = List.of(new HostAddress(host, port));

        com.vesoft.nebula.client.graph.pool.NebulaPoolConfig nebulaPoolConfig = new com.vesoft.nebula.client.graph.pool.NebulaPoolConfig();
        nebulaPoolConfig.setMaxConnSize(1);

        try {
            pool.init(addresses, nebulaPoolConfig);
            return pool.getSession(properties.getUsername(), properties.getPassword(), false);
        } catch (Exception e) {
            throw new ConnectionException("创建 Session 失败: " + address, e);
        }
    }

    private boolean healthCheck(SessionWrapper wrapper) {
        try {
            ResultSet result = wrapper.getSession().execute("YIELD 1");
            return result.isSucceeded();
        } catch (Exception e) {
            log.warn("Session 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    private void destroySession(SessionWrapper wrapper) {
        try {
            if (wrapper.getSession() != null) {
                wrapper.getSession().close();
            }
            monitor.recordConnectionDestroy();
        } catch (Exception e) {
            log.warn("关闭 Session 异常: {}", e.getMessage());
        }
    }

    public SessionPoolMonitor getMonitor() {
        return monitor;
    }

    public void init() {
        if (poolConfig.getInitStrategy() == NebulaSessionPoolConfig.InitStrategy.EAGER) {
            int initSize = poolConfig.getMinIdle();
            for (int i = 0; i < initSize; i++) {
                try {
                    SessionWrapper wrapper = borrowSession();
                    returnSession(wrapper);
                } catch (Exception e) {
                    log.warn("预热创建 Session 失败: {}", e.getMessage());
                    monitor.recordWarmupFailure();
                }
            }
        }
    }
}