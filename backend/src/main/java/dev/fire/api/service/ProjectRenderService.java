package dev.fire.api.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import dev.fire.api.domain.ProjectRender;
import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.RenderStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectRenderService {

    private final VideoProjectStore projectStore;
    private final MediaAssetStore assetStore;
    private final ProjectRenderStore renderStore;
    private final ProjectRenderWorker worker;
    private final MediaStorageService mediaStorage;
    private final GeneratedVideoStorageService generatedVideoStorage;

    public ProjectRenderService(
            VideoProjectStore projectStore,
            MediaAssetStore assetStore,
            ProjectRenderStore renderStore,
            ProjectRenderWorker worker,
            MediaStorageService mediaStorage,
            GeneratedVideoStorageService generatedVideoStorage) {
        this.projectStore = projectStore;
        this.assetStore = assetStore;
        this.renderStore = renderStore;
        this.worker = worker;
        this.mediaStorage = mediaStorage;
        this.generatedVideoStorage = generatedVideoStorage;
    }

    public ProjectRender start(UUID projectId) {
        var project = projectStore.getRequired(projectId);
        if (project.getStatus() == ProjectStatus.QUEUED || project.getStatus() == ProjectStatus.PROCESSING) {
            throw new InvalidRenderStateException("Generation must finish before final rendering starts");
        }
        var assets = assetStore.findVisualsByProject(projectId);
        var timelineCount = project.getScenes().stream()
                .mapToLong(scene -> hasGeneratedVideo(projectId, scene)
                        ? 1
                        : assets.stream().filter(asset -> scene.getId().equals(asset.sceneId())).count())
                .sum();
        if (timelineCount == 0) {
            throw new InvalidRenderStateException("Add source media or generate at least one scene before rendering");
        }
        if (timelineCount > 300) {
            throw new InvalidRenderStateException("A render supports up to 300 images and videos");
        }
        var render = renderStore.queue(projectId);
        worker.render(projectId);
        return render;
    }

    private boolean hasGeneratedVideo(UUID projectId, dev.fire.api.domain.VideoScene scene) {
        return scene.getStatus() == dev.fire.api.domain.SceneStatus.COMPLETED
                && scene.getPreviewUri() != null
                && scene.getPreviewUri().startsWith("/api/")
                && generatedVideoStorage.exists(projectId, scene.getId());
    }

    public Optional<ProjectRender> find(UUID projectId) {
        return renderStore.findByProject(projectId);
    }

    public RenderContent content(UUID projectId) {
        var render = renderStore.getRequired(projectId);
        if (render.getStatus() != RenderStatus.COMPLETED || render.getOutputStorageKey() == null) {
            throw new InvalidRenderStateException("The final video is not ready");
        }
        Path path = mediaStorage.prepareOutputPath(render.getOutputStorageKey());
        if (!Files.isRegularFile(path)) {
            throw new ProjectRenderNotFoundException(projectId);
        }
        return new RenderContent(render, path);
    }

    public record RenderContent(ProjectRender render, Path path) {
    }
}
