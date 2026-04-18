package cn.com.mfish.graph.exception;

/**
 * NebulaGraph 超时异常
 *
 * @author mfish
 * @date 2026-04-18
 */
public class TimeoutException extends NebulaGraphException {
    public TimeoutException(String message) {
        super(message);
    }

    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}