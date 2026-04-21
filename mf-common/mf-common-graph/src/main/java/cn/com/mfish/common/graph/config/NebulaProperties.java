package cn.com.mfish.common.graph.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NebulaProperties {
    private SingleConfig single = new SingleConfig();
    private ClusterConfig cluster = new ClusterConfig();
    private String username = "root";
    private String password = "nebula";
    private PoolConfig pool = new PoolConfig();
    private SpaceConfig space = new SpaceConfig();

    @Data
    public static class SingleConfig {
        private boolean enabled = false;
        private String addresses = "127.0.0.1:9669";
    }

    @Data
    public static class ClusterConfig {
        private boolean enabled = false;
        private List<String> addresses = new ArrayList<>();
    }

    @Data
    public static class PoolConfig {
        private int minConns = 5;
        private int maxConns = 20;
        private int timeout = 3000;
        private int idleTimeout = 60;
    }

    @Data
    public static class SpaceConfig {
        private String name = "plm_graph";
        private String charset = "utf8";
        private int replicaFactor = 1;
        private int partitionNum = 100;
    }
}
