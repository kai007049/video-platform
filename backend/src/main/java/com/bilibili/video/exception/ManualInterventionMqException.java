package com.bilibili.video.exception;

public class ManualInterventionMqException extends RuntimeException {
    public ManualInterventionMqException(String message) {
        super(message);
    }

    public ManualInterventionMqException(String message, Throwable cause) {
        super(message, cause);
    }
}
