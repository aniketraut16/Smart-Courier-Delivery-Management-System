package com.smartcourier.userservice.exception;

public class InvalidAvailabilityTransitionException extends RuntimeException {
    public InvalidAvailabilityTransitionException(String message) {
        super(message);
    }
}
