package dev.fire.api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class VideoScene {

    private final UUID id;
    private final int sequence;
    private final String prompt;
    private volatile SceneStatus status;
    private volatile String providerJobId;
    private volatile String previewUri;
    private volatile String errorCode;
    private volatile String providerModel;
    private volatile BigDecimal estimatedCostUsd;
    private volatile Instant submittedAt;
    private volatile Instant completedAt;
    private volatile boolean providerJobTerminal;

    public VideoScene(int sequence, String prompt) {
        this(UUID.randomUUID(), sequence, prompt, SceneStatus.PENDING, null, null, null,
                null, null, null, null, false);
    }

    private VideoScene(
            UUID id,
            int sequence,
            String prompt,
            SceneStatus status,
            String providerJobId,
            String previewUri,
            String errorCode,
            String providerModel,
            BigDecimal estimatedCostUsd,
            Instant submittedAt,
            Instant completedAt,
            boolean providerJobTerminal) {
        this.id = id;
        this.sequence = sequence;
        this.prompt = prompt;
        this.status = status;
        this.providerJobId = providerJobId;
        this.previewUri = previewUri;
        this.errorCode = errorCode;
        this.providerModel = providerModel;
        this.estimatedCostUsd = estimatedCostUsd;
        this.submittedAt = submittedAt;
        this.completedAt = completedAt;
        this.providerJobTerminal = providerJobTerminal;
    }

    public static VideoScene restore(
            UUID id,
            int sequence,
            String prompt,
            SceneStatus status,
            String providerJobId,
            String previewUri,
            String errorCode,
            String providerModel,
            BigDecimal estimatedCostUsd,
            Instant submittedAt,
            Instant completedAt,
            boolean providerJobTerminal) {
        return new VideoScene(id, sequence, prompt, status, providerJobId, previewUri, errorCode,
                providerModel, estimatedCostUsd, submittedAt, completedAt, providerJobTerminal);
    }

    public synchronized void markProcessing() {
        status = SceneStatus.PROCESSING;
        errorCode = null;
    }

    public synchronized void markSubmitted(String providerJobId, String providerModel, BigDecimal estimatedCostUsd) {
        this.providerJobId = providerJobId;
        this.providerModel = providerModel;
        this.estimatedCostUsd = estimatedCostUsd;
        this.submittedAt = Instant.now();
        this.completedAt = null;
        this.providerJobTerminal = false;
        this.previewUri = null;
        this.errorCode = null;
        this.status = SceneStatus.PROCESSING;
    }

    public synchronized void markCompleted(String providerJobId, String previewUri) {
        this.providerJobId = providerJobId;
        this.previewUri = previewUri;
        this.errorCode = null;
        this.status = SceneStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.providerJobTerminal = false;
    }

    public synchronized void markFailed(String errorCode) {
        this.errorCode = errorCode;
        this.status = SceneStatus.FAILED;
    }

    public synchronized void markProviderJobTerminal() {
        this.providerJobTerminal = true;
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

    public String getProviderModel() {
        return providerModel;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isProviderJobTerminal() {
        return providerJobTerminal;
    }
}
