package dev.fire.api.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProjectRenderWorker {

    private static final int MAX_LOG_SAMPLE_BYTES = 64 * 1024;

    private final VideoProjectStore projectStore;
    private final MediaAssetStore assetStore;
    private final ProjectRenderStore renderStore;
    private final MediaStorageService mediaStorage;
    private final FfmpegCommandFactory commandFactory;
    private final GeneratedVideoStorageService generatedVideoStorage;
    private final String ffmpegExecutable;
    private final String ffprobeExecutable;
    private final long timeoutMinutes;

    public ProjectRenderWorker(
            VideoProjectStore projectStore,
            MediaAssetStore assetStore,
            ProjectRenderStore renderStore,
            MediaStorageService mediaStorage,
            GeneratedVideoStorageService generatedVideoStorage,
            FfmpegCommandFactory commandFactory,
            @Value("${fire.render.ffmpeg-path:ffmpeg}") String ffmpegExecutable,
            @Value("${fire.render.ffprobe-path:ffprobe}") String ffprobeExecutable,
            @Value("${fire.render.timeout-minutes:30}") long timeoutMinutes) {
        this.projectStore = projectStore;
        this.assetStore = assetStore;
        this.renderStore = renderStore;
        this.mediaStorage = mediaStorage;
        this.generatedVideoStorage = generatedVideoStorage;
        this.commandFactory = commandFactory;
        this.ffmpegExecutable = ffmpegExecutable;
        this.ffprobeExecutable = ffprobeExecutable;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Async("renderExecutor")
    public void render(UUID projectId) {
        var render = renderStore.getRequired(projectId);
        Path temporaryOutput = null;
        try {
            render.markProcessing();
            renderStore.save(render);

            var project = projectStore.getRequired(projectId);
            var assets = assetStore.findVisualsByProject(projectId);
            var timelineCount = project.getScenes().stream()
                    .mapToLong(scene -> hasGeneratedVideo(projectId, scene)
                            ? 1
                            : assets.stream().filter(asset -> scene.getId().equals(asset.sceneId())).count())
                    .sum();
            if (timelineCount == 0) {
                throw new RenderExecutionException("RENDER_REQUIRES_MEDIA", "The timeline has no image or video");
            }
            if (timelineCount > 300) {
                throw new RenderExecutionException("TOO_MANY_TIMELINE_ITEMS", "A render supports up to 300 visual items");
            }

            var timeline = new ArrayList<FfmpegCommandFactory.TimelineInput>();
            for (var scene : project.getScenes()) {
                if (hasGeneratedVideo(projectId, scene)) {
                    var path = generatedVideoStorage.load(projectId, scene.getId());
                    timeline.add(new FfmpegCommandFactory.TimelineInput(
                            AssetKind.VIDEO,
                            "video/mp4",
                            path,
                            probeVideoDuration(path)));
                    continue;
                }
                for (var asset : assets) {
                    if (!scene.getId().equals(asset.sceneId())) {
                        continue;
                    }
                    var durationSeconds = durationSeconds(asset);
                    timeline.add(new FfmpegCommandFactory.TimelineInput(
                            asset.kind(),
                            asset.contentType(),
                            mediaStorage.resolveAssetPath(asset),
                            durationSeconds));
                }
            }
            var soundtrack = assetStore.findLatestSoundtrack(projectId)
                    .map(mediaStorage::resolveAssetPath)
                    .orElse(null);

            var outputKey = projectId + "/renders/" + render.getId() + ".mp4";
            var finalOutput = mediaStorage.prepareOutputPath(outputKey);
            temporaryOutput = finalOutput.resolveSibling(render.getId() + ".part.mp4");
            Files.deleteIfExists(temporaryOutput);

            var command = commandFactory.build(
                    ffmpegExecutable,
                    timeline,
                    soundtrack,
                    project.getRenderPreset(),
                    project.getAspectRatio(),
                    temporaryOutput);
            execute(command, timeoutMinutes, TimeUnit.MINUTES, "RENDER");
            if (!Files.isRegularFile(temporaryOutput) || Files.size(temporaryOutput) == 0) {
                throw new RenderExecutionException("RENDER_OUTPUT_MISSING", "FFmpeg did not produce an output file");
            }
            moveAtomically(temporaryOutput, finalOutput);
            temporaryOutput = null;
            render.markCompleted(outputKey);
            renderStore.save(render);
        } catch (RenderExecutionException exception) {
            deleteQuietly(temporaryOutput);
            render.markFailed(exception.getErrorCode());
            renderStore.save(render);
        } catch (RuntimeException | IOException exception) {
            deleteQuietly(temporaryOutput);
            render.markFailed("RENDER_FAILURE");
            renderStore.save(render);
        }
    }

    private boolean hasGeneratedVideo(UUID projectId, dev.fire.api.domain.VideoScene scene) {
        return scene.getStatus() == dev.fire.api.domain.SceneStatus.COMPLETED
                && scene.getPreviewUri() != null
                && scene.getPreviewUri().startsWith("/api/")
                && generatedVideoStorage.exists(projectId, scene.getId());
    }

    private double durationSeconds(MediaAsset asset) {
        if (asset.durationMs() != null) {
            return asset.durationMs() / 1000.0;
        }
        if (asset.kind() == AssetKind.IMAGE) {
            return 0.5;
        }
        return probeVideoDuration(mediaStorage.resolveAssetPath(asset));
    }

    private double probeVideoDuration(Path videoPath) {
        var command = List.of(
                ffprobeExecutable,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoPath.toString());
        var output = execute(command, 30, TimeUnit.SECONDS, "PROBE").trim();
        try {
            var seconds = Double.parseDouble(output);
            if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 3600) {
                throw new NumberFormatException("duration out of range");
            }
            return seconds;
        } catch (NumberFormatException exception) {
            throw new RenderExecutionException(
                    "VIDEO_PROBE_FAILED",
                    "Video duration could not be determined",
                    exception);
        }
    }

    private String execute(
            List<String> command,
            long timeout,
            TimeUnit unit,
            String operation) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new RenderExecutionException(
                    operation + "_ENGINE_UNAVAILABLE",
                    "The local media engine is unavailable",
                    exception);
        }

        var outputFuture = CompletableFuture.supplyAsync(() -> drainOutput(process.getInputStream()));
        try {
            if (!process.waitFor(timeout, unit)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new RenderExecutionException(operation + "_TIMEOUT", "The local media operation timed out");
            }
            var output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new RenderExecutionException(
                        operation + "_FAILED",
                        "The local media operation failed with exit code " + process.exitValue());
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new RenderExecutionException(operation + "_INTERRUPTED", "The local media operation was interrupted");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            process.destroyForcibly();
            throw new RenderExecutionException(operation + "_OUTPUT_FAILURE", "Media engine output could not be read");
        }
    }

    private String drainOutput(InputStream input) {
        var sample = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        try (input) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                var remaining = MAX_LOG_SAMPLE_BYTES - sample.size();
                if (remaining > 0) {
                    sample.write(buffer, 0, Math.min(read, remaining));
                }
            }
            return sample.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RenderExecutionException("RENDER_OUTPUT_FAILURE", "Media engine output could not be read");
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The render status carries the safe failure code.
        }
    }
}
