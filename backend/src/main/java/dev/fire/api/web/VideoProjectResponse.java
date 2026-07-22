package dev.fire.api.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.SceneStatus;
import dev.fire.api.domain.VideoProject;

public record VideoProjectResponse(
        UUID id,
        String title,
        String topic,
        String stylePrompt,
        String aspectRatio,
        ProjectStatus status,
        String errorCode,
        String providerName,
        Instant createdAt,
        Instant updatedAt,
        List<SceneResponse> scenes) {

    public static VideoProjectResponse from(VideoProject project) {
        return new VideoProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getTopic(),
                project.getStylePrompt(),
                project.getAspectRatio(),
                project.getStatus(),
                project.getErrorCode(),
                project.getProviderName(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getScenes().stream().map(scene -> new SceneResponse(
                        scene.getId(),
                        scene.getSequence(),
                        scene.getPrompt(),
                        scene.getStatus(),
                        scene.getProviderJobId(),
                        scene.getPreviewUri(),
                        scene.getErrorCode())).toList());
    }

    public record SceneResponse(
            UUID id,
            int sequence,
            String prompt,
            SceneStatus status,
            String providerJobId,
            String previewUri,
            String errorCode) {
    }
}
