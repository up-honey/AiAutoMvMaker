package dev.fire.api.domain;

import java.time.Instant;
import java.util.UUID;

public final class ProjectRender {

    private final UUID id;
    private final UUID projectId;
    private final Instant createdAt;
    private volatile RenderStatus status;
    private volatile String outputStorageKey;
    private volatile String errorCode;
    private volatile Instant updatedAt;

    public ProjectRender(UUID projectId) {
        this(UUID.randomUUID(), projectId, RenderStatus.QUEUED, null, null, Instant.now(), Instant.now());
    }

    private ProjectRender(
            UUID id,
            UUID projectId,
            RenderStatus status,
            String outputStorageKey,
            String errorCode,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.status = status;
        this.outputStorageKey = outputStorageKey;
        this.errorCode = errorCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProjectRender restore(
            UUID id,
            UUID projectId,
            RenderStatus status,
            String outputStorageKey,
            String errorCode,
            Instant createdAt,
            Instant updatedAt) {
        return new ProjectRender(id, projectId, status, outputStorageKey, errorCode, createdAt, updatedAt);
    }

    public synchronized void markProcessing() {
        status = RenderStatus.PROCESSING;
        errorCode = null;
        updatedAt = Instant.now();
    }

    public synchronized void markCompleted(String outputStorageKey) {
        this.status = RenderStatus.COMPLETED;
        this.outputStorageKey = outputStorageKey;
        this.errorCode = null;
        this.updatedAt = Instant.now();
    }

    public synchronized void markFailed(String errorCode) {
        this.status = RenderStatus.FAILED;
        this.errorCode = errorCode;
        this.updatedAt = Instant.now();
    }

    public synchronized void prepareForRecovery() {
        if (status != RenderStatus.QUEUED && status != RenderStatus.PROCESSING) {
            throw new IllegalStateException("RENDER_NOT_RECOVERABLE");
        }
        status = RenderStatus.QUEUED;
        errorCode = null;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public RenderStatus getStatus() { return status; }
    public String getOutputStorageKey() { return outputStorageKey; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
