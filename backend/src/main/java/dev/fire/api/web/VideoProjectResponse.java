package dev.fire.api.web;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import dev.fire.api.domain.ProjectRender;
import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.RenderPreset;
import dev.fire.api.domain.RenderStatus;
import dev.fire.api.domain.SceneStatus;
import dev.fire.api.domain.VideoProject;

public record VideoProjectResponse(
        UUID id,
        String title,
        String topic,
        String stylePrompt,
        String aspectRatio,
        RenderPreset renderPreset,
        ProjectStatus status,
        String errorCode,
        String providerName,
        Instant createdAt,
        Instant updatedAt,
        List<SceneResponse> scenes,
        List<AssetResponse> assets,
        RenderResponse render) {

    public static VideoProjectResponse from(
            VideoProject project,
            List<MediaAsset> assets,
            Optional<ProjectRender> render) {
        return new VideoProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getTopic(),
                project.getStylePrompt(),
                project.getAspectRatio(),
                project.getRenderPreset(),
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
                        scene.getErrorCode(),
                        scene.getProviderModel(),
                        scene.getEstimatedCostUsd(),
                        scene.getSubmittedAt(),
                        scene.getCompletedAt())).toList(),
                assets.stream().map(asset -> new AssetResponse(
                        asset.id(),
                        asset.sceneId(),
                        asset.kind(),
                        asset.originalFilename(),
                        asset.contentType(),
                        asset.sizeBytes(),
                        asset.sha256(),
                        "/api/projects/" + asset.projectId() + "/assets/" + asset.id() + "/content",
                        asset.timelinePosition(),
                        asset.durationMs(),
                        asset.createdAt())).toList(),
                render.map(VideoProjectResponse::renderResponse).orElse(null));
    }

    public record SceneResponse(
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
            Instant completedAt) {
    }

    public record AssetResponse(
            UUID id,
            UUID sceneId,
            AssetKind kind,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            String contentUrl,
            int timelinePosition,
            Integer durationMs,
            Instant createdAt) {
    }

    public static RenderResponse renderResponse(ProjectRender render) {
        var contentUrl = render.getStatus() == RenderStatus.COMPLETED
                ? "/api/projects/" + render.getProjectId() + "/render/content"
                : null;
        return new RenderResponse(
                render.getId(),
                render.getStatus(),
                render.getErrorCode(),
                contentUrl,
                render.getCreatedAt(),
                render.getUpdatedAt());
    }

    public record RenderResponse(
            UUID id,
            RenderStatus status,
            String errorCode,
            String contentUrl,
            Instant createdAt,
            Instant updatedAt) {
    }
}
