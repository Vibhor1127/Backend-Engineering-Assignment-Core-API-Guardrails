package com.grid07.api.exception;

// Thrown when any of the bot guardrails are hit
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
