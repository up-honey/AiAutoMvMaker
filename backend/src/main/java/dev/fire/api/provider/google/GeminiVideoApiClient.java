package dev.fire.api.provider.google;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.fire.api.provider.VideoProviderException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class GeminiVideoApiClient {

    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9_.-]+");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final String model;
    private final String apiRevision;
    private final Duration requestTimeout;
    private final Duration generationTimeout;
    private final Duration pollInterval;
    private final long maxOutputBytes;
    private final long maxJsonBytes;

    GeminiVideoApiClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String model,
            String apiRevision,
            Duration requestTimeout,
            Duration generationTimeout,
            Duration pollInterval,
            long maxOutputBytes) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUri = validateBaseUri(baseUrl);
        this.apiKey = requireValue(apiKey, "GEMINI_API_KEY");
        this.model = requireValue(model, "Gemini video model");
        this.apiRevision = requireValue(apiRevision, "Gemini API revision");
        this.requestTimeout = requestTimeout;
        this.generationTimeout = generationTimeout;
        this.pollInterval = pollInterval;
        this.maxOutputBytes = maxOutputBytes;
        this.maxJsonBytes = Math.addExact(Math.multiplyExact(maxOutputBytes, 2), 1024L * 1024L);
    }

    String submit(JsonNode input, String aspectRatio, String task) {
        var body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("input", input);
        body.put("background", true);
        body.put("store", true);

        var responseFormat = body.putObject("response_format");
        responseFormat.put("type", "video");
        responseFormat.put("aspect_ratio", aspectRatio);
        responseFormat.put("delivery", "uri");

        var videoConfig = body.putObject("generation_config").putObject("video_config");
        videoConfig.put("task", task);

        var response = sendJson("POST", "/v1beta/interactions", body);
        var interactionId = response.path("id").asText("");
        validateResourceId(interactionId, "GEMINI_INVALID_RESPONSE");
        return interactionId;
    }

    byte[] awaitVideo(String interactionId) {
        validateResourceId(interactionId, "GEMINI_INVALID_JOB_ID");
        var deadline = Instant.now().plus(generationTimeout);
        while (Instant.now().isBefore(deadline)) {
            var interaction = sendJson("GET", "/v1beta/interactions/" + interactionId, null);
            var status = interaction.path("status").asText("").toLowerCase(Locale.ROOT);
            if (status.equals("completed")) {
                return extractVideo(interaction);
            }
            if (status.equals("failed") || status.equals("cancelled") || status.equals("rejected")) {
                throw new VideoProviderException("GEMINI_GENERATION_REJECTED", true);
            }
            pause();
        }
        throw new VideoProviderException("GEMINI_GENERATION_TIMEOUT");
    }

    private byte[] extractVideo(JsonNode interaction) {
        for (var step : interaction.path("steps")) {
            if (!step.path("type").asText("").equals("model_output")) {
                continue;
            }
            for (var content : step.path("content")) {
                if (!content.path("type").asText("").equals("video")) {
                    continue;
                }
                var data = content.path("data").asText("");
                if (!data.isBlank()) {
                    return decodeVideo(data);
                }
                var uri = content.path("uri").asText("");
                if (!uri.isBlank()) {
                    return downloadVideo(uri);
                }
            }
        }
        throw new VideoProviderException("GEMINI_INVALID_RESPONSE", true);
    }

    private byte[] decodeVideo(String encoded) {
        try {
            var decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length > maxOutputBytes) {
                throw new VideoProviderException("GEMINI_OUTPUT_TOO_LARGE", true);
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new VideoProviderException("GEMINI_INVALID_RESPONSE", true);
        }
    }

    private byte[] downloadVideo(String providerUri) {
        final URI uri;
        try {
            uri = URI.create(providerUri);
        } catch (IllegalArgumentException exception) {
            throw new VideoProviderException("GEMINI_INVALID_RESPONSE", true);
        }
        if (!sameOrigin(uri, baseUri)) {
            throw new VideoProviderException("GEMINI_INVALID_RESPONSE", true);
        }
        var marker = "/v1beta/files/";
        var path = uri.getPath();
        var markerIndex = path.indexOf(marker);
        if (markerIndex < 0) {
            throw new VideoProviderException("GEMINI_INVALID_RESPONSE", true);
        }
        var fileId = path.substring(markerIndex + marker.length()).replace(":download", "");
        validateResourceId(fileId, "GEMINI_INVALID_RESPONSE");

        var deadline = Instant.now().plus(generationTimeout);
        while (Instant.now().isBefore(deadline)) {
            var file = sendJson("GET", "/v1beta/files/" + fileId, null);
            var state = file.path("state").asText("").toUpperCase(Locale.ROOT);
            if (state.equals("ACTIVE")) {
                return sendBytes("/v1beta/files/" + fileId + ":download?alt=media");
            }
            if (state.equals("FAILED")) {
                throw new VideoProviderException("GEMINI_GENERATION_REJECTED", true);
            }
            pause();
        }
        throw new VideoProviderException("GEMINI_GENERATION_TIMEOUT");
    }

    private JsonNode sendJson(String method, String path, JsonNode body) {
        try {
            var request = request(method, path, body == null ? null : objectMapper.writeValueAsBytes(body));
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    drainLimited(input, 64 * 1024L);
                    throw httpFailure(response.statusCode(), method.equals("GET"));
                }
                return objectMapper.readTree(readLimited(input, maxJsonBytes));
            }
        } catch (VideoProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("GEMINI_GENERATION_INTERRUPTED", exception);
        } catch (IOException | RuntimeException exception) {
            throw new VideoProviderException("GEMINI_UNAVAILABLE", exception);
        }
    }

    private byte[] sendBytes(String path) {
        try {
            var response = httpClient.send(request("GET", path, null), HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    drainLimited(input, 64 * 1024L);
                    throw httpFailure(response.statusCode(), true);
                }
                return readLimited(input, maxOutputBytes);
            }
        } catch (VideoProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("GEMINI_GENERATION_INTERRUPTED", exception);
        } catch (IOException exception) {
            throw new VideoProviderException("GEMINI_UNAVAILABLE", exception);
        }
    }

    private HttpRequest request(String method, String path, byte[] body) {
        var builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("x-goog-api-key", apiKey)
                .header("Api-Revision", apiRevision)
                .header("Accept", "application/json");
        if (body == null) {
            return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
        }
        return builder
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private VideoProviderException httpFailure(int statusCode, boolean providerJobExists) {
        return switch (statusCode) {
            case 400, 404, 422 -> new VideoProviderException("GEMINI_REQUEST_REJECTED", providerJobExists);
            case 401, 403 -> new VideoProviderException("GEMINI_AUTHENTICATION_FAILED");
            case 408 -> new VideoProviderException("GEMINI_REQUEST_TIMEOUT");
            case 429 -> new VideoProviderException("GEMINI_RATE_LIMITED");
            default -> new VideoProviderException("GEMINI_UNAVAILABLE");
        };
    }

    private void pause() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("GEMINI_GENERATION_INTERRUPTED", exception);
        }
    }

    private byte[] readLimited(InputStream input, long limit) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new VideoProviderException("GEMINI_RESPONSE_TOO_LARGE");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void drainLimited(InputStream input, long limit) throws IOException {
        var buffer = new byte[4096];
        long total = 0;
        int read;
        while (total < limit && (read = input.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) != -1) {
            total += read;
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme() != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost() != null
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private URI validateBaseUri(String baseUrl) {
        var normalized = requireValue(baseUrl, "Gemini API base URL").replaceAll("/+$", "") + "/";
        var uri = URI.create(normalized);
        if (uri.getHost() == null || !(uri.getScheme().equals("https") || uri.getScheme().equals("http"))) {
            throw new IllegalArgumentException("Gemini API base URL must be an HTTP(S) origin");
        }
        if (uri.getScheme().equals("http") && !isLoopback(uri.getHost())) {
            throw new IllegalArgumentException("Gemini API base URL must use HTTPS outside local tests");
        }
        return uri;
    }

    private boolean isLoopback(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("CHANGEME")) {
            throw new IllegalStateException(name + " must be configured when Gemini generation is enabled");
        }
        return value.trim();
    }

    private void validateResourceId(String value, String errorCode) {
        if (value == null || !RESOURCE_ID.matcher(value).matches()) {
            throw new VideoProviderException(errorCode);
        }
    }
}
