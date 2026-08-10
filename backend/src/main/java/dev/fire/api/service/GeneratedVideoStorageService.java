package dev.fire.api.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeneratedVideoStorageService {

    private static final long MEBIBYTE = 1024L * 1024L;

    private final Path storageRoot;
    private final long maxVideoBytes;

    public GeneratedVideoStorageService(
            @Value("${fire.media.storage-root:./data/media}") String storageRoot,
            @Value("${fire.media.max-generated-video-mb:100}") long maxGeneratedVideoMb) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxVideoBytes = maxGeneratedVideoMb * MEBIBYTE;
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException exception) {
            throw new MediaStorageException("Generated video storage could not be initialized", exception);
        }
    }

    public Path store(UUID projectId, UUID sceneId, byte[] videoBytes) {
        validateMp4(videoBytes);
        if (videoBytes.length > maxVideoBytes) {
            throw new MediaStorageException("Generated video exceeds the configured size limit", null);
        }

        var target = resolve(projectId, sceneId);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), sceneId + "-", ".part");
            Files.write(temporary, videoBytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new MediaStorageException("Generated video could not be stored", exception);
        }
    }

    public Path load(UUID projectId, UUID sceneId) {
        var path = resolve(projectId, sceneId);
        if (!Files.isRegularFile(path)) {
            throw new GeneratedVideoNotFoundException(sceneId);
        }
        return path;
    }

    public boolean exists(UUID projectId, UUID sceneId) {
        return Files.isRegularFile(resolve(projectId, sceneId));
    }

    private Path resolve(UUID projectId, UUID sceneId) {
        var resolved = storageRoot
                .resolve(projectId.toString())
                .resolve("generated")
                .resolve(sceneId + ".mp4")
                .normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new InvalidMediaException("INVALID_STORAGE_PATH", "Generated video storage path is invalid");
        }
        return resolved;
    }

    private void validateMp4(byte[] bytes) {
        if (bytes == null || bytes.length < 12
                || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            throw new InvalidMediaException("INVALID_GENERATED_VIDEO", "Provider output is not a valid MP4 file");
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original storage error.
        }
    }
}
