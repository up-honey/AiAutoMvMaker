package dev.fire.api.domain;

import java.util.UUID;

public final class VideoScene {

    private final UUID id;
    private final int sequence;
    private final String prompt;
    private volatile SceneStatus status;
    private volatile String providerJobId;
    private volatile String previewUri;
    private volatile String errorCode;

    public VideoScene(int sequence, String prompt) {
        this.id = UUID.randomUUID();
        this.sequence = sequence;
        this.prompt = prompt;
        this.status = SceneStatus.PENDING;
    }

    public synchronized void markProcessing() {
        status = SceneStatus.PROCESSING;
        errorCode = null;
    }

    public synchronized void markCompleted(String providerJobId, String previewUri) {
        this.providerJobId = providerJobId;
        this.previewUri = previewUri;
        this.errorCode = null;
        this.status = SceneStatus.COMPLETED;
    }

    public synchronized void markFailed(String errorCode) {
        this.errorCode = errorCode;
        this.status = SceneStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public int getSequence() {
        return sequence;
    }

    public String getPrompt() {
        return prompt;
    }

    public SceneStatus getStatus() {
        return status;
    }

    public String getProviderJobId() {
        return providerJobId;
    }

    public String getPreviewUri() {
        return previewUri;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
