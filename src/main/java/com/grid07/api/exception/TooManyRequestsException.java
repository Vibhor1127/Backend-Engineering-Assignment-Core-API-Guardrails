package com.grid07.api.exception;

// Thrown when a bot hits any of the guardrail limits
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
