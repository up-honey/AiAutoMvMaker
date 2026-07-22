package dev.fire.api.provider;

public interface VideoProvider {

    String name();

    VideoGenerationResult generate(VideoGenerationCommand command);
}
