package cn.com.mfish.graph.pool.impl;

import cn.com.mfish.graph.config.LoadBalanceConfig;
import cn.com.mfish.graph.config.NebulaGraphProperties;
import cn.com.mfish.graph.pool.AddressManager;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NebulaGraph 地址管理器实现类
 * 管理多地址场景下的地址可用性状态，支持故障检测和恢复
 *
 * @author mfish
 * @date 2026-04-18
 */
@Slf4j
public class AddressManagerImpl implements AddressManager {
    private final List<String> allAddresses;
    private final Set<String> unavailableAddresses;
    private final Map<String, AtomicInteger> failureCounters;
    private final Map<String, Long> lastFailureTimes;
    private final LoadBalanceConfig config;

    public AddressManagerImpl(List<String> addresses, LoadBalanceConfig config) {
        this.allAddresses = new ArrayList<>(addresses);
        this.unavailableAddresses = ConcurrentHashMap.newKeySet();
        this.failureCounters = new ConcurrentHashMap<>();
        this.lastFailureTimes = new ConcurrentHashMap<>();
        this.config = config;

        for (String address : addresses) {
            failureCounters.put(address, new AtomicInteger(0));
        }
    }

    @Override
    public void recordFailure(String address) {
        AtomicInteger counter = failureCounters.get(address);
        if (counter == null) {
            counter = new AtomicInteger(0);
            failureCounters.put(address, counter);
        }
        int count = counter.incrementAndGet();
        lastFailureTimes.put(address, System.currentTimeMillis());

        if (count >= FAILURE_THRESHOLD) {
            markUnavailable(address);
            log.warn("地址失败次数超过阈值，标记为不可用: address={}, count={}", address, count);
        }
    }

    @Override
    public String getAvailableAddress() {
        List<String> available = getAvailableAddresses();
        if (available.isEmpty()) {
            log.error("没有可用的 NebulaGraph 地址");
            return null;
        }

        for (String address : available) {
            if (!unavailableAddresses.contains(address)) {
                return address;
            }
        }
        return available.get(0);
    }

    @Override
    public void markAvailable(String address) {
        unavailableAddresses.remove(address);
        AtomicInteger counter = failureCounters.get(address);
        if (counter != null) {
            counter.set(0);
        }
        log.info("地址标记为可用: {}", address);
    }

    @Override
    public void markUnavailable(String address) {
        unavailableAddresses.add(address);
        log.warn("地址标记为不可用: {}", address);
    }

    @Override
    public Set<String> getUnavailableAddresses() {
        return Collections.unmodifiableSet(unavailableAddresses);
    }

    @Override
    public List<String> getAvailableAddresses() {
        List<String> available = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (String address : allAddresses) {
            if (unavailableAddresses.contains(address)) {
                Long lastFailure = lastFailureTimes.get(address);
                if (lastFailure != null && now - lastFailure > RECOVERY_INTERVAL * 1000) {
                    markAvailable(address);
                    available.add(address);
                }
            } else {
                available.add(address);
            }
        }

        return available;
    }

    @Override
    public int getFailureCount(String address) {
        AtomicInteger counter = failureCounters.get(address);
        return counter != null ? counter.get() : 0;
    }

    @Override
    public void resetFailureCount(String address) {
        AtomicInteger counter = failureCounters.get(address);
        if (counter != null) {
            counter.set(0);
        }
    }

    @Override
    public boolean isAvailable(String address) {
        return !unavailableAddresses.contains(address);
    }
}