package cn.com.mfish.graph.exception;

/**
 * NebulaGraph 业务异常
 *
 * @author mfish
 * @date 2026-04-18
 */
public class BusinessException extends NebulaGraphException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}