package dev.fire.api.provider;

import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;

public record VideoInputAsset(
        UUID assetId,
        AssetKind kind,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageKey,
        int timelinePosition,
        Integer durationMs) {

    public static VideoInputAsset from(MediaAsset asset) {
        return asset == null ? null : new VideoInputAsset(
                asset.id(),
                asset.kind(),
                asset.contentType(),
                asset.sizeBytes(),
                asset.sha256(),
                asset.storageKey(),
                asset.timelinePosition(),
                asset.durationMs());
    }
}
