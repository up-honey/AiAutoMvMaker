package dev.fire.api.provider;

public class VideoProviderException extends RuntimeException {

    private final String errorCode;

    public VideoProviderException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
