package dev.fire.api.web;

import java.util.List;

import dev.fire.api.domain.RenderPreset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateVideoProjectRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String topic,
        @NotBlank @Size(max = 1000) String stylePrompt,
        @NotBlank @Pattern(regexp = "16:9|9:16") String aspectRatio,
        RenderPreset renderPreset,
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 1000) String> scenePrompts) {
}
