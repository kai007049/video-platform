package com.kai.videoplatform.exception;

public class RetryableMqException extends RuntimeException {
    public RetryableMqException(String message) {
        super(message);
    }

    public RetryableMqException(String message, Throwable cause) {
        super(message, cause);
    }
}