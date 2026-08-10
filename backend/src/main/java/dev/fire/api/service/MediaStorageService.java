package dev.fire.api.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import dev.fire.api.domain.ProjectStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {

    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int SIGNATURE_LENGTH = 32;

    private final Path storageRoot;
    private final VideoProjectStore projectStore;
    private final MediaAssetStore assetStore;
    private final ProjectRenderStore renderStore;
    private final long maxImageBytes;
    private final long maxVideoBytes;
    private final long maxAudioBytes;

    public MediaStorageService(
            VideoProjectStore projectStore,
            MediaAssetStore assetStore,
            ProjectRenderStore renderStore,
            @Value("${fire.media.storage-root:./data/media}") String storageRoot,
            @Value("${fire.media.max-image-mb:15}") long maxImageMb,
            @Value("${fire.media.max-video-mb:200}") long maxVideoMb,
            @Value("${fire.media.max-audio-mb:30}") long maxAudioMb) {
        this.projectStore = projectStore;
        this.assetStore = assetStore;
        this.renderStore = renderStore;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageMb * MEBIBYTE;
        this.maxVideoBytes = maxVideoMb * MEBIBYTE;
        this.maxAudioBytes = maxAudioMb * MEBIBYTE;
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException exception) {
            throw new MediaStorageException("Media storage could not be initialized", exception);
        }
    }

    public MediaAsset store(
            UUID projectId,
            UUID sceneId,
            AssetKind kind,
            Integer timelinePosition,
            Integer durationMs,
            MultipartFile file) {
        var project = projectStore.getRequired(projectId);
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new InvalidMediaException(
                    "PROJECT_MEDIA_LOCKED",
                    "Media can only be added while the project is a draft");
        }
        renderStore.findByProject(projectId).ifPresent(render -> {
            if (render.getStatus() != dev.fire.api.domain.RenderStatus.FAILED) {
                throw new InvalidMediaException(
                        "PROJECT_MEDIA_LOCKED",
                        "Media cannot change after rendering has started");
            }
        });
        validateAssignment(projectId, sceneId, kind, project);
        var normalizedPosition = normalizePosition(kind, timelinePosition);
        var normalizedDuration = normalizeDuration(kind, durationMs);
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("EMPTY_MEDIA_FILE", "Choose a non-empty media file");
        }

        var limit = sizeLimit(kind);
        if (file.getSize() > limit) {
            throw tooLarge(kind, limit);
        }

        var assetId = UUID.randomUUID();
        Path temporaryPath = null;
        Path finalPath = null;
        try (var input = new BufferedInputStream(file.getInputStream())) {
            input.mark(SIGNATURE_LENGTH);
            var signature = input.readNBytes(SIGNATURE_LENGTH);
            input.reset();
            var detectedMedia = detectMedia(signature, kind);
            var storageKey = projectId + "/" + assetId + "." + detectedMedia.extension();
            finalPath = resolveStorageKey(storageKey);
            Files.createDirectories(finalPath.getParent());
            temporaryPath = finalPath.resolveSibling(finalPath.getFileName() + ".part");

            var digest = sha256();
            var bytesWritten = copyWithLimit(input, temporaryPath, limit, digest, kind);
            moveAtomically(temporaryPath, finalPath);
            temporaryPath = null;

            var asset = new MediaAsset(
                    assetId,
                    projectId,
                    sceneId,
                    kind,
                    sanitizeFilename(file.getOriginalFilename()),
                    detectedMedia.contentType(),
                    bytesWritten,
                    HexFormat.of().formatHex(digest.digest()),
                    storageKey,
                    normalizedPosition,
                    normalizedDuration,
                    Instant.now());
            try {
                return assetStore.save(asset);
            } catch (RuntimeException exception) {
                Files.deleteIfExists(finalPath);
                throw exception;
            }
        } catch (InvalidMediaException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(finalPath);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporaryPath);
            deleteQuietly(finalPath);
            throw new MediaStorageException("Media file could not be stored", exception);
        }
    }

    public MediaAsset store(UUID projectId, UUID sceneId, AssetKind kind, MultipartFile file) {
        return store(projectId, sceneId, kind, null, null, file);
    }

    private int normalizePosition(AssetKind kind, Integer timelinePosition) {
        if (kind == AssetKind.AUDIO) {
            return 0;
        }
        var position = timelinePosition == null ? 0 : timelinePosition;
        if (position < 0 || position > 9999) {
            throw new InvalidMediaException(
                    "INVALID_TIMELINE_POSITION",
                    "Timeline position must be between 0 and 9999");
        }
        return position;
    }

    private Integer normalizeDuration(AssetKind kind, Integer durationMs) {
        if (kind == AssetKind.AUDIO) {
            return null;
        }
        if (kind == AssetKind.IMAGE) {
            var duration = durationMs == null ? 500 : durationMs;
            if (duration < 100 || duration > 60_000) {
                throw new InvalidMediaException(
                        "INVALID_MEDIA_DURATION",
                        "Image duration must be between 100 and 60000 milliseconds");
            }
            return duration;
        }
        if (durationMs != null && (durationMs < 100 || durationMs > 600_000)) {
            throw new InvalidMediaException(
                    "INVALID_MEDIA_DURATION",
                    "Video trim duration must be between 100 and 600000 milliseconds");
        }
        return durationMs;
    }

    public AssetContent load(UUID projectId, UUID assetId) {
        var asset = assetStore.getRequired(projectId, assetId);
        var path = resolveStorageKey(asset.storageKey());
        if (!Files.isRegularFile(path)) {
            throw new MediaAssetNotFoundException(assetId);
        }
        return new AssetContent(asset, path);
    }

    Path resolveAssetPath(MediaAsset asset) {
        var path = resolveStorageKey(asset.storageKey());
        if (!Files.isRegularFile(path)) {
            throw new MediaAssetNotFoundException(asset.id());
        }
        return path;
    }

    Path prepareOutputPath(String storageKey) {
        var path = resolveStorageKey(storageKey);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException exception) {
            throw new MediaStorageException("Render output directory could not be created", exception);
        }
        return path;
    }

    private void validateAssignment(
            UUID projectId,
            UUID sceneId,
            AssetKind kind,
            dev.fire.api.domain.VideoProject project) {
        if (kind == AssetKind.AUDIO) {
            if (sceneId != null) {
                throw new InvalidMediaException(
                        "INVALID_MEDIA_ASSIGNMENT",
                        "A soundtrack belongs to the project, not to one scene");
            }
            return;
        }
        if (sceneId == null) {
            throw new InvalidMediaException(
                    "INVALID_MEDIA_ASSIGNMENT",
                    "An image or video must be assigned to a scene");
        }
        var sceneBelongsToProject = project.getScenes().stream()
                .anyMatch(scene -> scene.getId().equals(sceneId));
        if (!sceneBelongsToProject) {
            throw new InvalidMediaException(
                    "SCENE_NOT_IN_PROJECT",
                    "The selected scene does not belong to project " + projectId);
        }
    }

    private long sizeLimit(AssetKind kind) {
        return switch (kind) {
            case IMAGE -> maxImageBytes;
            case VIDEO -> maxVideoBytes;
            case AUDIO -> maxAudioBytes;
        };
    }

    private InvalidMediaException tooLarge(AssetKind kind, long limit) {
        return new InvalidMediaException(
                "MEDIA_FILE_TOO_LARGE",
                kind + " files must be " + (limit / MEBIBYTE) + " MB or smaller");
    }

    private long copyWithLimit(
            InputStream input,
            Path target,
            long limit,
            MessageDigest digest,
            AssetKind kind) throws IOException {
        long total = 0;
        var buffer = new byte[8192];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw tooLarge(kind, limit);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private DetectedMedia detectMedia(byte[] bytes, AssetKind requestedKind) {
        var detected = switch (requestedKind) {
            case IMAGE -> detectImage(bytes);
            case VIDEO -> detectVideo(bytes);
            case AUDIO -> detectAudio(bytes);
        };
        if (detected == null) {
            throw new InvalidMediaException(
                    "UNSUPPORTED_MEDIA_TYPE",
                    "The file signature does not match a supported " + requestedKind.name().toLowerCase() + " format");
        }
        return detected;
    }

    private DetectedMedia detectImage(byte[] bytes) {
        if (startsWith(bytes, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) {
            return new DetectedMedia("image/png", "png");
        }
        if (startsWith(bytes, 0xff, 0xd8, 0xff)) {
            return new DetectedMedia("image/jpeg", "jpg");
        }
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WEBP")) {
            return new DetectedMedia("image/webp", "webp");
        }
        if (asciiAt(bytes, 0, "GIF87a") || asciiAt(bytes, 0, "GIF89a")) {
            return new DetectedMedia("image/gif", "gif");
        }
        return null;
    }

    private DetectedMedia detectVideo(byte[] bytes) {
        if (asciiAt(bytes, 4, "ftyp")) {
            return new DetectedMedia("video/mp4", "mp4");
        }
        if (startsWith(bytes, 0x1a, 0x45, 0xdf, 0xa3)) {
            return new DetectedMedia("video/webm", "webm");
        }
        return null;
    }

    private DetectedMedia detectAudio(byte[] bytes) {
        if (asciiAt(bytes, 0, "ID3") || isMp3Frame(bytes)) {
            return new DetectedMedia("audio/mpeg", "mp3");
        }
        if (asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WAVE")) {
            return new DetectedMedia("audio/wav", "wav");
        }
        if (asciiAt(bytes, 0, "OggS")) {
            return new DetectedMedia("audio/ogg", "ogg");
        }
        if (asciiAt(bytes, 4, "ftyp")) {
            return new DetectedMedia("audio/mp4", "m4a");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff
                && ((bytes[1] & 0xff) == 0xf1 || (bytes[1] & 0xff) == 0xf9)) {
            return new DetectedMedia("audio/aac", "aac");
        }
        return null;
    }

    private boolean isMp3Frame(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xff) == 0xff
                && ((bytes[1] & 0xe0) == 0xe0);
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xff) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiAt(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if ((char) bytes[offset + index] != value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private Path resolveStorageKey(String storageKey) {
        var resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new InvalidMediaException("INVALID_STORAGE_PATH", "Media storage path is invalid");
        }
        return resolved;
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }
        var normalized = originalFilename.replace('\\', '/');
        var lastSlash = normalized.lastIndexOf('/');
        var basename = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        basename = basename.replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isBlank()) {
            return "upload";
        }
        return basename.length() > 255 ? basename.substring(0, 255) : basename;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
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
            // The original storage error is more useful to the caller.
        }
    }

    public record AssetContent(MediaAsset asset, Path path) {
    }

    private record DetectedMedia(String contentType, String extension) {
    }
}
