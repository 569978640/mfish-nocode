package cn.com.mfish.graph.exception;

/**
 * NebulaGraph Schema 异常
 *
 * @author mfish
 * @date 2026-04-18
 */
public class SchemaException extends NebulaGraphException {
    public SchemaException(String message) {
        super(message);
    }

    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}