package dev.fire.api.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.fire.api.domain.SceneStatus;
import dev.fire.api.provider.VideoGenerationCommand;
import dev.fire.api.provider.VideoInputAsset;
import dev.fire.api.provider.VideoProvider;
import dev.fire.api.provider.VideoProviderException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class VideoGenerationOrchestrator {

    private final VideoProjectStore store;
    private final MediaAssetStore assetStore;
    private final Map<String, VideoProvider> providers;

    public VideoGenerationOrchestrator(
            VideoProjectStore store,
            MediaAssetStore assetStore,
            List<VideoProvider> providers) {
        this.store = store;
        this.assetStore = assetStore;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.name().toLowerCase(Locale.ROOT),
                Function.identity()));
    }

    public List<String> providerNames() {
        return providers.keySet().stream().sorted().toList();
    }

    public boolean supports(String providerName) {
        return providers.containsKey(providerName.toLowerCase(Locale.ROOT));
    }

    @Async("generationExecutor")
    public void generate(UUID projectId, String providerName) {
        var project = store.getRequired(projectId);
        var provider = providers.get(providerName.toLowerCase(Locale.ROOT));
        if (provider == null) {
            project.markFailed("UNKNOWN_PROVIDER");
            store.save(project);
            return;
        }

        project.markProcessing();
        store.save(project);
        var soundtrack = assetStore.findLatestSoundtrack(projectId)
                .map(VideoInputAsset::from)
                .orElse(null);
        for (var scene : project.getScenes()) {
            if (scene.getStatus() == SceneStatus.COMPLETED) {
                continue;
            }

            scene.markProcessing();
            project.recordProgress();
            store.save(project);
            try {
                var command = new VideoGenerationCommand(
                        project.getId(),
                        scene.getId(),
                        scene.getPrompt(),
                        project.getStylePrompt(),
                        project.getAspectRatio(),
                        assetStore.findForScene(projectId, scene.getId()).stream()
                                .map(VideoInputAsset::from)
                                .toList(),
                        soundtrack);
                if (scene.getProviderJobId() == null || scene.isProviderJobTerminal()) {
                    var submission = provider.submit(command);
                    scene.markSubmitted(
                            submission.providerJobId(),
                            provider.model(),
                            provider.estimatedCostUsd(command));
                    project.recordProgress();
                    store.save(project);
                }
                var result = provider.awaitResult(command, scene.getProviderJobId());
                scene.markCompleted(result.providerJobId(), result.previewUri());
                project.recordProgress();
                store.save(project);
            } catch (VideoProviderException exception) {
                if (exception.shouldDiscardProviderJob()) {
                    scene.markProviderJobTerminal();
                }
                scene.markFailed(exception.getErrorCode());
                project.markFailed(exception.getErrorCode());
                store.save(project);
                return;
            } catch (RuntimeException exception) {
                scene.markFailed("PROVIDER_FAILURE");
                project.markFailed("PROVIDER_FAILURE");
                store.save(project);
                return;
            }
        }
        project.markCompleted();
        store.save(project);
    }
}
