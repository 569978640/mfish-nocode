package cn.com.mfish.graph.pool;

import com.vesoft.nebula.client.graph.net.Session;
import lombok.Data;

/**
 * NebulaGraph Session 包装器
 * 封装 Session 实例及其状态信息，用于连接池管理
 *
 * @author mfish
 * @date 2026-04-18
 */
@Data
public class SessionWrapper {
    /**
     * Nebula Session 实例
     */
    private Session session;

    /**
     * Session 状态
     */
    private SessionState state;

    /**
     * 最后使用时间（用于空闲回收和泄漏检测）
     */
    private long lastUsedTime;

    /**
     * Session 创建时间（用于生命周期管理）
     */
    private long createTime;

    /**
     * 所属 Nebula 图存储地址
     */
    private String address;

    /**
     * 所属业务线（用于熔断隔离）
     */
    private String businessLine;

    /**
     * Session 状态枚举
     */
    public enum SessionState {
        /**
         * 空闲，在池中等待借取
         */
        IDLE,
        /**
         * 活跃，已被借取使用
         */
        ACTIVE,
        /**
         * 失效，待销毁重建
         */
        INVALID
    }

    /**
     * 创建空闲状态的 SessionWrapper
     *
     * @param session Session 实例
     * @param address 所属地址
     * @return SessionWrapper 实例
     */
    public static SessionWrapper createIdle(Session session, String address) {
        SessionWrapper wrapper = new SessionWrapper();
        wrapper.setSession(session);
        wrapper.setState(SessionState.IDLE);
        wrapper.setAddress(address);
        wrapper.setCreateTime(System.currentTimeMillis());
        wrapper.setLastUsedTime(System.currentTimeMillis());
        return wrapper;
    }

    /**
     * 标记为活跃状态
     */
    public void markActive() {
        this.state = SessionState.ACTIVE;
        this.lastUsedTime = System.currentTimeMillis();
    }

    /**
     * 标记为空闲状态
     */
    public void markIdle() {
        this.state = SessionState.IDLE;
        this.lastUsedTime = System.currentTimeMillis();
    }

    /**
     * 标记为失效状态
     */
    public void markInvalid() {
        this.state = SessionState.INVALID;
    }

    /**
     * 检查是否有效
     *
     * @return true=有效，false=失效
     */
    public boolean isValid() {
        return state != SessionState.INVALID && session != null;
    }

    /**
     * 获取空闲时间（ms）
     *
     * @return 空闲时间
     */
    public long getIdleTime() {
        return System.currentTimeMillis() - lastUsedTime;
    }

    /**
     * 获取存活时间（ms）
     *
     * @return 存活时间
     */
    public long getAliveTime() {
        return System.currentTimeMillis() - createTime;
    }
}