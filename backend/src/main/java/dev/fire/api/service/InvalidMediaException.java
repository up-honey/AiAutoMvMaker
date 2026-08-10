package dev.fire.api.service;

public class InvalidMediaException extends RuntimeException {

    private final String errorCode;

    public InvalidMediaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
