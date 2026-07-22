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

    public VideoProject(
            String title,
            String topic,
            String stylePrompt,
            String aspectRatio,
            List<VideoScene> scenes) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.topic = topic;
        this.stylePrompt = stylePrompt;
        this.aspectRatio = aspectRatio;
        this.scenes = List.copyOf(scenes);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.status = ProjectStatus.DRAFT;
    }

    public synchronized void queue() {
        if (status != ProjectStatus.DRAFT && status != ProjectStatus.FAILED) {
            throw new IllegalStateException("PROJECT_NOT_STARTABLE");
        }
        status = ProjectStatus.QUEUED;
        errorCode = null;
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
}
