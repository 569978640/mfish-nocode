package cn.com.mfish.graph.exception;

/**
 * NebulaGraph 连接异常
 *
 * @author mfish
 * @date 2026-04-18
 */
public class ConnectionException extends NebulaGraphException {
    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}