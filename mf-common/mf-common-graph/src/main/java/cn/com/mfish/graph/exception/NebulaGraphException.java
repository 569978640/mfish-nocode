package cn.com.mfish.graph.exception;

/**
 * NebulaGraph 统一异常基类
 *
 * @author mfish
 * @date 2026-04-18
 */
public class NebulaGraphException extends RuntimeException {
    public NebulaGraphException(String message) {
        super(message);
    }

    public NebulaGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}