package cn.com.mfish.common.graph.config;

import com.vesoft.nebula.client.graph.NebulaPoolConfig;
import com.vesoft.nebula.client.graph.data.HostAddress;
import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.exception.AuthFailedException;
import com.vesoft.nebula.client.graph.exception.ClientServerIncompatibleException;
import com.vesoft.nebula.client.graph.exception.IOErrorException;
import com.vesoft.nebula.client.graph.exception.NotValidConnectionException;
import com.vesoft.nebula.client.graph.net.NebulaPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class NebulaConfig {

    @Bean
    @ConfigurationProperties(prefix = "nebula")
    public NebulaProperties nebulaProperties() {
        return new NebulaProperties();
    }

    @Bean
    public NebulaPool nebulaPool(NebulaProperties properties) {
        NebulaPoolConfig poolConfig = new NebulaPoolConfig();
        poolConfig.setMaxConnSize(properties.getPool().getMaxConns());
        poolConfig.setMinConnSize(properties.getPool().getMinConns());
        poolConfig.setTimeout(properties.getPool().getTimeout());
        poolConfig.setIdleTime(properties.getPool().getIdleTimeout());

        List<HostAddress> addresses = parseAddresses(properties);

        NebulaPool pool = new NebulaPool();
        try {
            pool.init(addresses, poolConfig);
            log.info("NebulaGraph 连接池初始化成功: {}", addresses);
        } catch (UnknownHostException e) {
            log.error("NebulaGraph 连接池初始化失败", e);
            throw new RuntimeException("NebulaGraph 连接池初始化失败", e);
        }
        return pool;
    }

    private List<HostAddress> parseAddresses(NebulaProperties properties) {
        List<HostAddress> addresses = new ArrayList<>();
        if (properties.getSingle() != null && properties.getSingle().isEnabled()) {
            String[] parts = properties.getSingle().getAddresses().split(",");
            for (String address : parts) {
                String[] hostPort = address.trim().split(":");
                if (hostPort.length == 2) {
                    addresses.add(new HostAddress(hostPort[0].trim(), Integer.parseInt(hostPort[1].trim())));
                } else {
                    addresses.add(new HostAddress(address.trim(), 9669));
                }
            }
        } else if (properties.getCluster() != null && properties.getCluster().isEnabled()) {
            for (String address : properties.getCluster().getAddresses()) {
                String[] hostPort = address.split(":");
                if (hostPort.length == 2) {
                    addresses.add(new HostAddress(hostPort[0].trim(), Integer.parseInt(hostPort[1].trim())));
                } else {
                    addresses.add(new HostAddress(address.trim(), 9669));
                }
            }
        }
        return addresses;
    }

    @Bean
    public NebulaTemplate nebulaTemplate(NebulaPool nebulaPool, NebulaProperties properties) {
        return new NebulaTemplate(nebulaPool, properties.getSpace().getName(),
                properties.getUsername(), properties.getPassword());
    }

    public static class NebulaTemplate {
        private final NebulaPool pool;
        private final String spaceName;
        private final String username;
        private final String password;

        public NebulaTemplate(NebulaPool pool, String spaceName, String username, String password) {
            this.pool = pool;
            this.spaceName = spaceName;
            this.username = username;
            this.password = password;
        }

        public NebulaPool getPool() {
            return pool;
        }

        public String getSpaceName() {
            return spaceName;
        }

        public ResultSet execute(String nGql) throws IOErrorException, AuthFailedException, ClientServerIncompatibleException, NotValidConnectionException {
            try (var session = pool.getSession(username, password, false)) {
                session.execute("USE " + spaceName);
                return session.execute(nGql);
            }
        }

        public void executeWithoutResult(String nGql) throws IOErrorException, AuthFailedException, ClientServerIncompatibleException, NotValidConnectionException {
            try (var session = pool.getSession(username, password, false)) {
                session.execute("USE " + spaceName);
                session.execute(nGql);
            }
        }
    }
}
