package dev.fire.api.provider;

import java.util.UUID;
import java.util.List;

public record VideoGenerationCommand(
        UUID projectId,
        UUID sceneId,
        String prompt,
        String stylePrompt,
        String aspectRatio,
        List<VideoInputAsset> sourceAssets,
        VideoInputAsset soundtrack) {
}
