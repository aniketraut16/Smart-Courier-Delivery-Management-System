package com.smartcourier.userservice.exception;

public class MissingCourierDetailsException extends RuntimeException {
    public MissingCourierDetailsException(String message) {
        super(message);
    }
}
