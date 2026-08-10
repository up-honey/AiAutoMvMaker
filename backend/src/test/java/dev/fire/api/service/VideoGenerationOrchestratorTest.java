package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import dev.fire.api.provider.VideoGenerationCommand;
import dev.fire.api.provider.VideoGenerationResult;
import dev.fire.api.provider.VideoGenerationSubmission;
import dev.fire.api.provider.VideoProvider;
import org.junit.jupiter.api.Test;

class VideoGenerationOrchestratorTest {

    @Test
    void passesSceneMediaAndSoundtrackToTheProviderAdapter() {
        var projectStore = mock(VideoProjectStore.class);
        var assetStore = mock(MediaAssetStore.class);
        var captured = new AtomicReference<VideoGenerationCommand>();
        var project = new VideoProject(
                "Input-aware generation",
                "Uploaded media",
                "cinematic",
                "9:16",
                List.of(new VideoScene(1, "Animate this photo")));
        var sceneAsset = asset(project, project.getScenes().getFirst().getId(), AssetKind.IMAGE, "scene/image.png");
        var secondSceneAsset = asset(project, project.getScenes().getFirst().getId(), AssetKind.VIDEO, "scene/clip.mp4");
        var soundtrack = asset(project, null, AssetKind.AUDIO, "audio/music.mp3");
        var provider = new VideoProvider() {
            @Override
            public String name() {
                return "capture";
            }

            @Override
            public VideoGenerationSubmission submit(VideoGenerationCommand command) {
                captured.set(command);
                return new VideoGenerationSubmission("job-1");
            }

            @Override
            public VideoGenerationResult awaitResult(VideoGenerationCommand command, String providerJobId) {
                return new VideoGenerationResult(providerJobId, "mock://job-1.mp4");
            }
        };

        when(projectStore.getRequired(project.getId())).thenReturn(project);
        when(projectStore.save(any(VideoProject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetStore.findForScene(project.getId(), project.getScenes().getFirst().getId()))
                .thenReturn(List.of(sceneAsset, secondSceneAsset));
        when(assetStore.findLatestSoundtrack(project.getId())).thenReturn(Optional.of(soundtrack));

        new VideoGenerationOrchestrator(projectStore, assetStore, List.of(provider))
                .generate(project.getId(), "capture");

        assertThat(captured.get().sourceAssets()).hasSize(2);
        assertThat(captured.get().sourceAssets().getFirst().assetId()).isEqualTo(sceneAsset.id());
        assertThat(captured.get().sourceAssets().getFirst().sha256()).isEqualTo(sceneAsset.sha256());
        assertThat(captured.get().sourceAssets().get(1).assetId()).isEqualTo(secondSceneAsset.id());
        assertThat(captured.get().soundtrack().assetId()).isEqualTo(soundtrack.id());
    }

    @Test
    void resumesAPersistedProviderJobWithoutSubmittingAndPayingAgain() {
        var projectStore = mock(VideoProjectStore.class);
        var assetStore = mock(MediaAssetStore.class);
        var project = new VideoProject(
                "Recovery",
                "Do not submit twice",
                "cinematic",
                "9:16",
                List.of(new VideoScene(1, "Resume this scene")));
        var scene = project.getScenes().getFirst();
        scene.markSubmitted("persisted-job", "gemini-omni-flash-preview", new BigDecimal("1.0000"));
        scene.prepareForRecovery();
        var submissions = new AtomicInteger();
        var awaitedJob = new AtomicReference<String>();
        var provider = new VideoProvider() {
            @Override
            public String name() {
                return "capture";
            }

            @Override
            public VideoGenerationSubmission submit(VideoGenerationCommand command) {
                submissions.incrementAndGet();
                return new VideoGenerationSubmission("unexpected-new-job");
            }

            @Override
            public VideoGenerationResult awaitResult(VideoGenerationCommand command, String providerJobId) {
                awaitedJob.set(providerJobId);
                return new VideoGenerationResult(providerJobId, "mock://persisted-job.mp4");
            }
        };

        when(projectStore.getRequired(project.getId())).thenReturn(project);
        when(projectStore.save(any(VideoProject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assetStore.findForScene(project.getId(), scene.getId())).thenReturn(List.of());
        when(assetStore.findLatestSoundtrack(project.getId())).thenReturn(Optional.empty());

        new VideoGenerationOrchestrator(projectStore, assetStore, List.of(provider))
                .generate(project.getId(), "capture");

        assertThat(submissions).hasValue(0);
        assertThat(awaitedJob).hasValue("persisted-job");
        assertThat(scene.getProviderJobId()).isEqualTo("persisted-job");
    }

    private MediaAsset asset(VideoProject project, UUID sceneId, AssetKind kind, String storageKey) {
        return new MediaAsset(
                UUID.randomUUID(),
                project.getId(),
                sceneId,
                kind,
                storageKey.substring(storageKey.indexOf('/') + 1),
                kind == AssetKind.AUDIO ? "audio/mpeg" : "image/png",
                100,
                "a".repeat(64),
                storageKey,
                0,
                kind == AssetKind.IMAGE ? 500 : null,
                Instant.now());
    }
}
