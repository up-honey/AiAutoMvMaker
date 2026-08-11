package dev.fire.api.service;

public class RenderExecutionException extends RuntimeException {

    private final String errorCode;

    public RenderExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RenderExecutionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
