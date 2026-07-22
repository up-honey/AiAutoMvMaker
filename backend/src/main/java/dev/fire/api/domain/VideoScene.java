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
        this(UUID.randomUUID(), sequence, prompt, SceneStatus.PENDING, null, null, null);
    }

    private VideoScene(
            UUID id,
            int sequence,
            String prompt,
            SceneStatus status,
            String providerJobId,
            String previewUri,
            String errorCode) {
        this.id = id;
        this.sequence = sequence;
        this.prompt = prompt;
        this.status = status;
        this.providerJobId = providerJobId;
        this.previewUri = previewUri;
        this.errorCode = errorCode;
    }

    public static VideoScene restore(
            UUID id,
            int sequence,
            String prompt,
            SceneStatus status,
            String providerJobId,
            String previewUri,
            String errorCode) {
        return new VideoScene(id, sequence, prompt, status, providerJobId, previewUri, errorCode);
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

    public synchronized void prepareForRecovery() {
        if (status == SceneStatus.PROCESSING) {
            status = SceneStatus.PENDING;
            errorCode = null;
        }
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
