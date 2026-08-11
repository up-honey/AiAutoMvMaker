package dev.fire.api.domain;

import java.time.Instant;
import java.util.UUID;

public record MediaAsset(
        UUID id,
        UUID projectId,
        UUID sceneId,
        AssetKind kind,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageKey,
        int timelinePosition,
        Integer durationMs,
        Instant createdAt) {
}
