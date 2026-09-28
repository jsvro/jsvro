package io.github.jsvro.core;

public class JsvroException extends RuntimeException {
    public JsvroException(String message) {
        super(message);
    }

    public JsvroException(String message, Throwable cause) {
        super(message, cause);
    }
}
