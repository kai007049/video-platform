package com.bilibili.video.exception;

public class RetryableMqException extends RuntimeException {
    public RetryableMqException(String message) {
        super(message);
    }

    public RetryableMqException(String message, Throwable cause) {
        super(message, cause);
    }
}
