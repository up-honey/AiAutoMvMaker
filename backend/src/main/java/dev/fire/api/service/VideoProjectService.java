package dev.fire.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import dev.fire.api.web.CreateVideoProjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VideoProjectService {

    private final VideoProjectStore store;
    private final VideoGenerationOrchestrator orchestrator;
    private final String defaultProvider;

    public VideoProjectService(
            VideoProjectStore store,
            VideoGenerationOrchestrator orchestrator,
            @Value("${fire.video.default-provider:mock}") String defaultProvider) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.defaultProvider = defaultProvider;
    }

    public VideoProject create(CreateVideoProjectRequest request) {
        var scenes = new ArrayList<VideoScene>();
        for (int index = 0; index < request.scenePrompts().size(); index++) {
            scenes.add(new VideoScene(index + 1, request.scenePrompts().get(index).trim()));
        }

        return store.save(new VideoProject(
                request.title().trim(),
                request.topic().trim(),
                request.stylePrompt().trim(),
                request.aspectRatio(),
                scenes));
    }

    public List<VideoProject> list() {
        return store.findAll();
    }

    public VideoProject get(UUID projectId) {
        return store.getRequired(projectId);
    }

    public VideoProject start(UUID projectId, String requestedProvider) {
        var providerName = requestedProvider == null || requestedProvider.isBlank()
                ? defaultProvider
                : requestedProvider.trim().toLowerCase();

        if (!orchestrator.supports(providerName)) {
            throw new UnknownProviderException(providerName);
        }

        var project = store.queueForGeneration(projectId, providerName);
        orchestrator.generate(projectId, providerName);
        return project;
    }

    public List<String> providers() {
        return orchestrator.providerNames();
    }
}
