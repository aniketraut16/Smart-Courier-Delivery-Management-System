package com.smartcourier.userservice.exception;

public class CourierProfileNotFoundException extends RuntimeException {
    public CourierProfileNotFoundException(String message) {
        super(message);
    }
}
