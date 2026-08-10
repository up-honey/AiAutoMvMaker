package dev.fire.api.provider.google;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.provider.VideoGenerationCommand;
import dev.fire.api.provider.VideoGenerationResult;
import dev.fire.api.provider.VideoGenerationSubmission;
import dev.fire.api.provider.VideoInputAsset;
import dev.fire.api.provider.VideoProvider;
import dev.fire.api.provider.VideoProviderException;
import dev.fire.api.service.GeneratedVideoStorageService;
import dev.fire.api.service.InvalidMediaException;
import dev.fire.api.service.MediaStorageException;
import dev.fire.api.service.MediaStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(
        prefix = "fire.video.gemini",
        name = {"enabled", "paid-generation-enabled"},
        havingValue = "true")
public class GeminiVideoProvider implements VideoProvider {

    private static final long MEBIBYTE = 1024L * 1024L;
    private static final int MAX_IMAGES = 8;
    private static final List<String> SUPPORTED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp");

    private final GeminiVideoApiClient apiClient;
    private final MediaStorageService mediaStorage;
    private final GeneratedVideoStorageService generatedVideoStorage;
    private final long maxInlineInputBytes;
    private final ObjectMapper objectMapper;
    private final String model;
    private final BigDecimal estimatedCostUsd;

    public GeminiVideoProvider(
            MediaStorageService mediaStorage,
            GeneratedVideoStorageService generatedVideoStorage,
            ObjectMapper objectMapper,
            @Value("${fire.video.gemini.api-key:}") String apiKey,
            @Value("${fire.video.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${fire.video.gemini.model:gemini-omni-flash-preview}") String model,
            @Value("${fire.video.gemini.api-revision:2026-05-20}") String apiRevision,
            @Value("${fire.video.gemini.request-timeout-seconds:30}") long requestTimeoutSeconds,
            @Value("${fire.video.gemini.generation-timeout-minutes:15}") long generationTimeoutMinutes,
            @Value("${fire.video.gemini.poll-interval-seconds:5}") long pollIntervalSeconds,
            @Value("${fire.video.gemini.max-inline-input-mb:20}") long maxInlineInputMb,
            @Value("${fire.video.gemini.max-output-mb:100}") long maxOutputMb,
            @Value("${fire.video.gemini.estimated-cost-per-second-usd:0.10}") String estimatedCostPerSecondUsd,
            @Value("${fire.video.gemini.estimated-output-seconds:10}") int estimatedOutputSeconds) {
        this(
                new GeminiVideoApiClient(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)))
                                .build(),
                        objectMapper,
                        baseUrl,
                        apiKey,
                        model,
                        apiRevision,
                        Duration.ofSeconds(Math.max(1, requestTimeoutSeconds)),
                        Duration.ofMinutes(Math.max(1, generationTimeoutMinutes)),
                        Duration.ofSeconds(Math.max(1, pollIntervalSeconds)),
                        Math.multiplyExact(maxOutputMb, MEBIBYTE)),
                mediaStorage,
                generatedVideoStorage,
                objectMapper,
                Math.multiplyExact(maxInlineInputMb, MEBIBYTE),
                model,
                new BigDecimal(estimatedCostPerSecondUsd)
                        .multiply(BigDecimal.valueOf(estimatedOutputSeconds))
                        .setScale(4, RoundingMode.HALF_UP));
    }

    GeminiVideoProvider(
            GeminiVideoApiClient apiClient,
            MediaStorageService mediaStorage,
            GeneratedVideoStorageService generatedVideoStorage,
            ObjectMapper objectMapper,
            long maxInlineInputBytes,
            String model,
            BigDecimal estimatedCostUsd) {
        this.apiClient = apiClient;
        this.mediaStorage = mediaStorage;
        this.generatedVideoStorage = generatedVideoStorage;
        this.objectMapper = objectMapper;
        this.maxInlineInputBytes = maxInlineInputBytes;
        this.model = model;
        this.estimatedCostUsd = estimatedCostUsd;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public BigDecimal estimatedCostUsd(VideoGenerationCommand command) {
        return estimatedCostUsd;
    }

    @Override
    public VideoGenerationSubmission submit(VideoGenerationCommand command) {
        var request = input(command);
        var jobId = apiClient.submit(request.input(), command.aspectRatio(), request.task());
        return new VideoGenerationSubmission(jobId);
    }

    @Override
    public VideoGenerationResult awaitResult(VideoGenerationCommand command, String providerJobId) {
        var video = apiClient.awaitVideo(providerJobId);
        try {
            generatedVideoStorage.store(command.projectId(), command.sceneId(), video);
        } catch (InvalidMediaException | MediaStorageException exception) {
            throw new VideoProviderException("GEMINI_INVALID_OUTPUT", true);
        }
        return new VideoGenerationResult(
                providerJobId,
                "/api/projects/" + command.projectId() + "/scenes/" + command.sceneId() + "/generated");
    }

    private GeminiInput input(VideoGenerationCommand command) {
        var assets = command.sourceAssets();
        var videos = assets.stream().filter(asset -> asset.kind() == AssetKind.VIDEO).toList();
        var images = assets.stream().filter(asset -> asset.kind() == AssetKind.IMAGE).toList();
        if (assets.size() != videos.size() + images.size()) {
            throw new VideoProviderException("GEMINI_UNSUPPORTED_INPUT");
        }
        if (videos.size() > 1 || (!videos.isEmpty() && !images.isEmpty())) {
            throw new VideoProviderException("GEMINI_UNSUPPORTED_INPUT");
        }
        if (images.size() > MAX_IMAGES) {
            throw new VideoProviderException("GEMINI_TOO_MANY_REFERENCE_IMAGES");
        }

        var prompt = combinedPrompt(command);
        if (assets.isEmpty()) {
            return new GeminiInput(objectMapper.getNodeFactory().textNode(prompt), "text_to_video");
        }

        long totalBytes = 0;
        if (!videos.isEmpty()) {
            var loaded = load(videos.getFirst(), command);
            validateVideo(loaded.asset(), loaded.bytes());
            totalBytes = loaded.bytes().length;
            validateTotalSize(totalBytes);
            var content = objectMapper.createArrayNode();
            content.add(mediaBlock("video", loaded.asset().contentType(), loaded.bytes()));
            content.add(textBlock(prompt));
            var userInput = objectMapper.createObjectNode();
            userInput.put("type", "user_input");
            userInput.set("content", content);
            var input = objectMapper.createArrayNode();
            input.add(userInput);
            return new GeminiInput(input, "edit");
        }

        var input = objectMapper.createArrayNode();
        for (var image : images) {
            var loaded = load(image, command);
            validateImage(loaded.asset());
            totalBytes = Math.addExact(totalBytes, loaded.bytes().length);
            validateTotalSize(totalBytes);
            input.add(mediaBlock("image", loaded.asset().contentType(), loaded.bytes()));
        }
        input.add(textBlock(prompt));
        return new GeminiInput(input, images.size() == 1 ? "image_to_video" : "reference_to_video");
    }

    private LoadedInput load(VideoInputAsset expected, VideoGenerationCommand command) {
        try {
            var content = mediaStorage.load(command.projectId(), expected.assetId());
            var asset = content.asset();
            if (asset.kind() != expected.kind()
                    || !asset.contentType().equalsIgnoreCase(expected.contentType())
                    || !asset.sha256().equalsIgnoreCase(expected.sha256())
                    || asset.sizeBytes() != expected.sizeBytes()
                    || asset.timelinePosition() != expected.timelinePosition()
                    || !java.util.Objects.equals(asset.durationMs(), expected.durationMs())) {
                throw new VideoProviderException("GEMINI_INPUT_CHANGED");
            }
            if (asset.sizeBytes() > maxInlineInputBytes) {
                throw new VideoProviderException("GEMINI_INPUT_TOO_LARGE");
            }
            var bytes = Files.readAllBytes(content.path());
            if (bytes.length != asset.sizeBytes()
                    || !sha256(bytes).equalsIgnoreCase(asset.sha256())) {
                throw new VideoProviderException("GEMINI_INPUT_CHANGED");
            }
            return new LoadedInput(VideoInputAsset.from(asset), bytes);
        } catch (VideoProviderException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new VideoProviderException("GEMINI_INPUT_UNAVAILABLE", exception);
        }
    }

    private void validateImage(VideoInputAsset asset) {
        if (!SUPPORTED_IMAGE_TYPES.contains(asset.contentType())) {
            throw new VideoProviderException("GEMINI_UNSUPPORTED_INPUT");
        }
    }

    private void validateVideo(VideoInputAsset asset, byte[] bytes) {
        if (!asset.contentType().equals("video/mp4")
                || bytes.length < 12
                || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            throw new VideoProviderException("GEMINI_UNSUPPORTED_INPUT");
        }
        if (asset.durationMs() != null && asset.durationMs() > 10_000) {
            throw new VideoProviderException("GEMINI_VIDEO_TOO_LONG");
        }
    }

    private void validateTotalSize(long bytes) {
        if (bytes > maxInlineInputBytes) {
            throw new VideoProviderException("GEMINI_INPUT_TOO_LARGE");
        }
    }

    private JsonNode mediaBlock(String type, String contentType, byte[] bytes) {
        var block = objectMapper.createObjectNode();
        block.put("type", type);
        block.put("mime_type", contentType);
        block.put("data", Base64.getEncoder().encodeToString(bytes));
        return block;
    }

    private JsonNode textBlock(String prompt) {
        var block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("text", prompt);
        return block;
    }

    private String combinedPrompt(VideoGenerationCommand command) {
        if (command.stylePrompt() == null || command.stylePrompt().isBlank()) {
            return command.prompt();
        }
        return command.prompt() + "\nVisual style: " + command.stylePrompt();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record GeminiInput(JsonNode input, String task) {
    }

    private record LoadedInput(VideoInputAsset asset, byte[] bytes) {
    }
}
