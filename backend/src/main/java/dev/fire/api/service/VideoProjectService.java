package dev.fire.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import dev.fire.api.domain.RenderPreset;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import dev.fire.api.web.CreateVideoProjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoProjectService {

    private final VideoProjectStore store;
    private final VideoGenerationOrchestrator orchestrator;
    private final MediaAssetStore assetStore;
    private final MediaStorageService mediaStorage;
    private final GeneratedVideoStorageService generatedVideoStorage;
    private final String defaultProvider;

    public VideoProjectService(
            VideoProjectStore store,
            VideoGenerationOrchestrator orchestrator,
            MediaAssetStore assetStore,
            MediaStorageService mediaStorage,
            GeneratedVideoStorageService generatedVideoStorage,
            @Value("${fire.video.default-provider:mock}") String defaultProvider) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.assetStore = assetStore;
        this.mediaStorage = mediaStorage;
        this.generatedVideoStorage = generatedVideoStorage;
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
                request.renderPreset() == null ? RenderPreset.CLEAN : request.renderPreset(),
                scenes));
    }

    public List<VideoProject> list() {
        return store.findAll();
    }

    public VideoProject get(UUID projectId) {
        return store.getRequired(projectId);
    }

    public List<MediaAsset> assets(UUID projectId) {
        return assetStore.findByProject(projectId);
    }

    public MediaAsset addAsset(
            UUID projectId,
            UUID sceneId,
            AssetKind kind,
            Integer timelinePosition,
            Integer durationMs,
            MultipartFile file) {
        return mediaStorage.store(projectId, sceneId, kind, timelinePosition, durationMs, file);
    }

    public MediaStorageService.AssetContent assetContent(UUID projectId, UUID assetId) {
        return mediaStorage.load(projectId, assetId);
    }

    public VideoProject start(UUID projectId, String requestedProvider, boolean paidGenerationConfirmed) {
        var providerName = requestedProvider == null || requestedProvider.isBlank()
                ? defaultProvider
                : requestedProvider.trim().toLowerCase();

        if (!orchestrator.supports(providerName)) {
            throw new UnknownProviderException(providerName);
        }
        if (!providerName.equals("mock") && !paidGenerationConfirmed) {
            throw new PaidGenerationConfirmationRequiredException(providerName);
        }

        var project = store.queueForGeneration(projectId, providerName);
        orchestrator.generate(projectId, providerName);
        return project;
    }

    public List<String> providers() {
        return orchestrator.providerNames();
    }

    public java.nio.file.Path generatedVideoContent(UUID projectId, UUID sceneId) {
        var project = store.getRequired(projectId);
        var belongsToProject = project.getScenes().stream().anyMatch(scene -> scene.getId().equals(sceneId));
        if (!belongsToProject) {
            throw new GeneratedVideoNotFoundException(sceneId);
        }
        return generatedVideoStorage.load(projectId, sceneId);
    }
}
