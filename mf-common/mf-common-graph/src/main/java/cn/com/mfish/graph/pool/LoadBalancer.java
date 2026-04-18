package cn.com.mfish.graph.pool;

import cn.com.mfish.graph.config.NebulaGraphProperties;

import java.util.List;

/**
 * NebulaGraph 负载均衡器接口
 * 根据负载均衡策略选择合适的地址
 *
 * @author mfish
 * @date 2026-04-18
 */
public interface LoadBalancer {
    /**
     * 选择地址（根据配置的负载均衡策略）
     *
     * @param availableAddresses 可用地址列表
     * @return 选中的地址
     */
    String selectAddress(List<String> availableAddresses);

    /**
     * 更新地址权重（用于加权策略）
     *
     * @param address 地址
     * @param weight 权重值
     */
    void updateWeight(String address, int weight);

    /**
     * 获取当前负载均衡策略
     *
     * @return 策略类型
     */
    NebulaGraphProperties.LoadBalanceStrategy getStrategy();

    /**
     * 静态工厂方法创建负载均衡器
     *
     * @param strategy 负载均衡策略
     * @return 负载均衡器实例
     */
    static LoadBalancer create(NebulaGraphProperties.LoadBalanceStrategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case WEIGHTED -> new WeightedLoadBalancer();
            case FAILOVER -> new FailoverLoadBalancer();
        };
    }

    /**
     * 轮询负载均衡器
     */
    class RoundRobinLoadBalancer implements LoadBalancer {
        private int cursor = 0;

        @Override
        public String selectAddress(List<String> availableAddresses) {
            if (availableAddresses == null || availableAddresses.isEmpty()) {
                return null;
            }
            int index = cursor % availableAddresses.size();
            cursor++;
            return availableAddresses.get(index);
        }

        @Override
        public void updateWeight(String address, int weight) {
        }

        @Override
        public NebulaGraphProperties.LoadBalanceStrategy getStrategy() {
            return NebulaGraphProperties.LoadBalanceStrategy.ROUND_ROBIN;
        }
    }

    /**
     * 加权负载均衡器
     */
    class WeightedLoadBalancer implements LoadBalancer {
        private final java.util.concurrent.ConcurrentHashMap<String, Integer> weights = new java.util.concurrent.ConcurrentHashMap<>();
        private int totalWeight = 0;
        private int cursor = 0;

        @Override
        public String selectAddress(List<String> availableAddresses) {
            if (availableAddresses == null || availableAddresses.isEmpty()) {
                return null;
            }
            synchronized (this) {
                if (totalWeight <= 0) {
                    for (String address : availableAddresses) {
                        int weight = weights.getOrDefault(address, 1);
                        totalWeight += weight;
                    }
                }
                int index = cursor % totalWeight;
                int sum = 0;
                for (String address : availableAddresses) {
                    sum += weights.getOrDefault(address, 1);
                    if (index < sum) {
                        cursor++;
                        return address;
                    }
                }
                cursor++;
                return availableAddresses.get(0);
            }
        }

        @Override
        public void updateWeight(String address, int weight) {
            weights.put(address, weight);
        }

        @Override
        public NebulaGraphProperties.LoadBalanceStrategy getStrategy() {
            return NebulaGraphProperties.LoadBalanceStrategy.WEIGHTED;
        }
    }

    /**
     * 故障优先负载均衡器
     * 优先选择最近未失败的地址
     */
    class FailoverLoadBalancer implements LoadBalancer {
        private final java.util.concurrent.ConcurrentHashMap<String, Long> lastFailureTime = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String selectAddress(List<String> availableAddresses) {
            if (availableAddresses == null || availableAddresses.isEmpty()) {
                return null;
            }
            long now = System.currentTimeMillis();
            String bestAddress = null;
            long earliestFailure = 0;

            for (String address : availableAddresses) {
                Long lastFailure = lastFailureTime.get(address);
                if (lastFailure == null) {
                    return address;
                }
                if (lastFailure <= earliestFailure) {
                    earliestFailure = lastFailure;
                    bestAddress = address;
                }
            }

            return bestAddress != null ? bestAddress : availableAddresses.get(0);
        }

        @Override
        public void updateWeight(String address, int weight) {
        }

        @Override
        public NebulaGraphProperties.LoadBalanceStrategy getStrategy() {
            return NebulaGraphProperties.LoadBalanceStrategy.FAILOVER;
        }

        /**
         * 记录地址失败时间
         *
         * @param address 地址
         */
        public void recordFailure(String address) {
            lastFailureTime.put(address, System.currentTimeMillis());
        }
    }
}