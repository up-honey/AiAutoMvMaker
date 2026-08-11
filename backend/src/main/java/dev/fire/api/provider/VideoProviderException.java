package dev.fire.api.provider;

public class VideoProviderException extends RuntimeException {

    private final String errorCode;
    private final boolean discardProviderJob;

    public VideoProviderException(String errorCode) {
        this(errorCode, false, null);
    }

    public VideoProviderException(String errorCode, Throwable cause) {
        this(errorCode, false, cause);
    }

    public VideoProviderException(String errorCode, boolean discardProviderJob) {
        this(errorCode, discardProviderJob, null);
    }

    private VideoProviderException(String errorCode, boolean discardProviderJob, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
        this.discardProviderJob = discardProviderJob;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean shouldDiscardProviderJob() {
        return discardProviderJob;
    }
}
