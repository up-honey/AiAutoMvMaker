package dev.fire.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VideoProject {

    private final UUID id;
    private final String title;
    private final String topic;
    private final String stylePrompt;
    private final String aspectRatio;
    private final List<VideoScene> scenes;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private volatile ProjectStatus status;
    private volatile String errorCode;
    private volatile String providerName;

    public VideoProject(
            String title,
            String topic,
            String stylePrompt,
            String aspectRatio,
            List<VideoScene> scenes) {
        this(
                UUID.randomUUID(),
                title,
                topic,
                stylePrompt,
                aspectRatio,
                scenes,
                Instant.now(),
                Instant.now(),
                ProjectStatus.DRAFT,
                null,
                null);
    }

    private VideoProject(
            UUID id,
            String title,
            String topic,
            String stylePrompt,
            String aspectRatio,
            List<VideoScene> scenes,
            Instant createdAt,
            Instant updatedAt,
            ProjectStatus status,
            String errorCode,
            String providerName) {
        this.id = id;
        this.title = title;
        this.topic = topic;
        this.stylePrompt = stylePrompt;
        this.aspectRatio = aspectRatio;
        this.scenes = List.copyOf(scenes);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
        this.errorCode = errorCode;
        this.providerName = providerName;
    }

    public static VideoProject restore(
            UUID id,
            String title,
            String topic,
            String stylePrompt,
            String aspectRatio,
            List<VideoScene> scenes,
            Instant createdAt,
            Instant updatedAt,
            ProjectStatus status,
            String errorCode,
            String providerName) {
        return new VideoProject(
                id,
                title,
                topic,
                stylePrompt,
                aspectRatio,
                scenes,
                createdAt,
                updatedAt,
                status,
                errorCode,
                providerName);
    }

    public synchronized void queue() {
        queue("mock");
    }

    public synchronized void queue(String providerName) {
        if (status != ProjectStatus.DRAFT && status != ProjectStatus.FAILED) {
            throw new IllegalStateException("PROJECT_NOT_STARTABLE");
        }
        status = ProjectStatus.QUEUED;
        errorCode = null;
        this.providerName = providerName;
        touch();
    }

    public synchronized void markProcessing() {
        status = ProjectStatus.PROCESSING;
        touch();
    }

    public synchronized void markCompleted() {
        status = ProjectStatus.COMPLETED;
        errorCode = null;
        touch();
    }

    public synchronized void markFailed(String errorCode) {
        this.errorCode = errorCode;
        this.status = ProjectStatus.FAILED;
        touch();
    }

    public synchronized void recordProgress() {
        touch();
    }

    public synchronized void prepareForRecovery(String fallbackProvider) {
        if (status != ProjectStatus.QUEUED && status != ProjectStatus.PROCESSING) {
            throw new IllegalStateException("PROJECT_NOT_RECOVERABLE");
        }
        scenes.forEach(VideoScene::prepareForRecovery);
        status = ProjectStatus.QUEUED;
        errorCode = null;
        if (providerName == null || providerName.isBlank()) {
            providerName = fallbackProvider;
        }
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getTopic() {
        return topic;
    }

    public String getStylePrompt() {
        return stylePrompt;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public List<VideoScene> getScenes() {
        return scenes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getProviderName() {
        return providerName;
    }
}
