package dev.fire.api.provider;

import java.util.UUID;

public record VideoGenerationCommand(
        UUID projectId,
        UUID sceneId,
        String prompt,
        String stylePrompt,
        String aspectRatio) {
}
