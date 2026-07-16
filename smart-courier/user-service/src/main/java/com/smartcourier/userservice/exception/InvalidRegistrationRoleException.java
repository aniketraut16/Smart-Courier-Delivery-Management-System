package com.smartcourier.userservice.exception;

public class InvalidRegistrationRoleException extends RuntimeException {
    public InvalidRegistrationRoleException(String message) {
        super(message);
    }
}
