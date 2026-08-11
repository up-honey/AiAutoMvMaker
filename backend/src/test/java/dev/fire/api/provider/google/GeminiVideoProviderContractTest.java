package dev.fire.api.provider.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.MediaAsset;
import dev.fire.api.provider.VideoGenerationCommand;
import dev.fire.api.provider.VideoInputAsset;
import dev.fire.api.service.GeneratedVideoStorageService;
import dev.fire.api.service.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class GeminiVideoProviderContractTest {

    private static final String API_KEY = "test-api-key-that-must-stay-in-the-header";
    private static final byte[] MP4 = new byte[] {
            0, 0, 0, 16, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void submitsAndPollsPromptOnlyVideoWithoutCallingGoogle() throws Exception {
        try (var server = new FakeGeminiServer(MP4)) {
            var objectMapper = new ObjectMapper();
            var provider = provider(server, objectMapper, mock(MediaStorageService.class));
            var command = command(List.of());

            var submission = provider.submit(command);
            var result = provider.awaitResult(command, submission.providerJobId());

            var request = objectMapper.readTree(server.createBody.get());
            assertThat(server.createApiKey.get()).isEqualTo(API_KEY);
            assertThat(server.createApiRevision.get()).isEqualTo("2026-05-20");
            assertThat(server.createQuery.get()).isNull();
            assertThat(server.createBody.get()).doesNotContain(API_KEY);
            assertThat(request.path("model").asText()).isEqualTo("gemini-omni-flash-preview");
            assertThat(request.path("input").asText()).contains("A calm opening shot", "cinematic");
            assertThat(request.path("background").asBoolean()).isTrue();
            assertThat(request.path("store").asBoolean()).isTrue();
            assertThat(request.path("response_format").path("type").asText()).isEqualTo("video");
            assertThat(request.path("response_format").path("aspect_ratio").asText()).isEqualTo("9:16");
            assertThat(request.path("response_format").path("delivery").asText()).isEqualTo("uri");
            assertThat(request.path("generation_config").path("video_config").path("task").asText())
                    .isEqualTo("text_to_video");
            assertThat(submission.providerJobId()).isEqualTo("job_123");
            assertThat(result.previewUri()).isEqualTo(
                    "/api/projects/" + command.projectId() + "/scenes/" + command.sceneId() + "/generated");
            assertThat(Files.readAllBytes(temporaryDirectory
                    .resolve(command.projectId().toString())
                    .resolve("generated")
                    .resolve(command.sceneId() + ".mp4"))).isEqualTo(MP4);
        }
    }

    @Test
    void sendsAHashVerifiedReferenceImageUsingTheDocumentedRestShape() throws Exception {
        try (var server = new FakeGeminiServer(MP4)) {
            var objectMapper = new ObjectMapper();
            var mediaStorage = mock(MediaStorageService.class);
            var projectId = UUID.randomUUID();
            var sceneId = UUID.randomUUID();
            var imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4};
            var imagePath = temporaryDirectory.resolve("reference.png");
            Files.write(imagePath, imageBytes);
            var asset = new MediaAsset(
                    UUID.randomUUID(),
                    projectId,
                    sceneId,
                    AssetKind.IMAGE,
                    "reference.png",
                    "image/png",
                    imageBytes.length,
                    sha256(imageBytes),
                    projectId + "/reference.png",
                    0,
                    500,
                    Instant.now());
            when(mediaStorage.load(projectId, asset.id()))
                    .thenReturn(new MediaStorageService.AssetContent(asset, imagePath));
            var command = new VideoGenerationCommand(
                    projectId,
                    sceneId,
                    "Make the leaves move in a gentle breeze",
                    "natural light",
                    "16:9",
                    List.of(VideoInputAsset.from(asset)),
                    null);

            provider(server, objectMapper, mediaStorage).submit(command);

            var request = objectMapper.readTree(server.createBody.get());
            assertThat(request.path("input").get(0).path("type").asText()).isEqualTo("image");
            assertThat(request.path("input").get(0).path("mime_type").asText()).isEqualTo("image/png");
            assertThat(request.path("input").get(0).path("data").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString(imageBytes));
            assertThat(request.path("input").get(1).path("text").asText())
                    .contains("gentle breeze", "natural light");
            assertThat(request.path("generation_config").path("video_config").path("task").asText())
                    .isEqualTo("image_to_video");
        }
    }

    private GeminiVideoProvider provider(
            FakeGeminiServer server,
            ObjectMapper objectMapper,
            MediaStorageService mediaStorage) {
        var client = new GeminiVideoApiClient(
                HttpClient.newHttpClient(),
                objectMapper,
                server.baseUrl(),
                API_KEY,
                "gemini-omni-flash-preview",
                "2026-05-20",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofMillis(1),
                1024 * 1024);
        return new GeminiVideoProvider(
                client,
                mediaStorage,
                new GeneratedVideoStorageService(temporaryDirectory.toString(), 1),
                objectMapper,
                1024 * 1024,
                "gemini-omni-flash-preview",
                new BigDecimal("1.0000"));
    }

    private VideoGenerationCommand command(List<VideoInputAsset> assets) {
        return new VideoGenerationCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "A calm opening shot",
                "cinematic",
                "9:16",
                assets,
                null);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class FakeGeminiServer implements AutoCloseable {

        private final HttpServer server;
        private final byte[] video;
        private final AtomicReference<String> createBody = new AtomicReference<>();
        private final AtomicReference<String> createApiKey = new AtomicReference<>();
        private final AtomicReference<String> createApiRevision = new AtomicReference<>();
        private final AtomicReference<String> createQuery = new AtomicReference<>();

        private FakeGeminiServer(byte[] video) throws IOException {
            this.video = video;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1beta/interactions/job_123", this::poll);
            server.createContext("/v1beta/interactions", this::create);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void create(HttpExchange exchange) throws IOException {
            createApiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            createApiRevision.set(exchange.getRequestHeaders().getFirst("Api-Revision"));
            createQuery.set(exchange.getRequestURI().getRawQuery());
            createBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(exchange, 200, "{\"id\":\"job_123\",\"status\":\"in_progress\"}");
        }

        private void poll(HttpExchange exchange) throws IOException {
            var encoded = Base64.getEncoder().encodeToString(video);
            json(exchange, 200, """
                    {"id":"job_123","status":"completed","steps":[
                      {"type":"model_output","content":[
                        {"type":"video","mime_type":"video/mp4","data":"%s"}
                      ]}
                    ]}
                    """.formatted(encoded));
        }

        private void json(HttpExchange exchange, int status, String body) throws IOException {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
