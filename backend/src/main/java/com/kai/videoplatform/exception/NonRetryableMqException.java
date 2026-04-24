package com.kai.videoplatform.exception;

public class NonRetryableMqException extends RuntimeException {
    public NonRetryableMqException(String message) {
        super(message);
    }

    public NonRetryableMqException(String message, Throwable cause) {
        super(message, cause);
    }
}