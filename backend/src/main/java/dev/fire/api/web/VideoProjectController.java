package dev.fire.api.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.service.ProjectRenderService;
import dev.fire.api.service.VideoProjectService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class VideoProjectController {

    private final VideoProjectService service;
    private final ProjectRenderService renderService;

    public VideoProjectController(VideoProjectService service, ProjectRenderService renderService) {
        this.service = service;
        this.renderService = renderService;
    }

    @PostMapping("/projects")
    public ResponseEntity<VideoProjectResponse> create(@Valid @RequestBody CreateVideoProjectRequest request) {
        var project = service.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(response(project));
    }

    @GetMapping("/projects")
    public List<VideoProjectResponse> list() {
        return service.list().stream().map(this::response).toList();
    }

    @GetMapping("/projects/{projectId}")
    public VideoProjectResponse get(@PathVariable UUID projectId) {
        return response(service.get(projectId));
    }

    @PostMapping("/projects/{projectId}/generate")
    public ResponseEntity<VideoProjectResponse> generate(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "false") boolean confirmPaid) {
        return ResponseEntity.accepted().body(response(service.start(projectId, provider, confirmPaid)));
    }

    @PostMapping(value = "/projects/{projectId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoProjectResponse.AssetResponse> uploadAsset(
            @PathVariable UUID projectId,
            @RequestParam AssetKind kind,
            @RequestParam(required = false) UUID sceneId,
            @RequestParam(required = false) Integer timelinePosition,
            @RequestParam(required = false) Integer durationMs,
            @RequestPart("file") MultipartFile file) {
        var asset = service.addAsset(projectId, sceneId, kind, timelinePosition, durationMs, file);
        var projectResponse = response(service.get(projectId));
        var response = projectResponse.assets().stream()
                .filter(candidate -> candidate.id().equals(asset.id()))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.created(URI.create(response.contentUrl())).body(response);
    }

    @GetMapping("/projects/{projectId}/assets/{assetId}/content")
    public ResponseEntity<FileSystemResource> assetContent(
            @PathVariable UUID projectId,
            @PathVariable UUID assetId) {
        var content = service.assetContent(projectId, assetId);
        var headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.asset().originalFilename(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(content.asset().contentType()))
                .contentLength(content.asset().sizeBytes())
                .body(new FileSystemResource(content.path()));
    }

    @PostMapping("/projects/{projectId}/render")
    public ResponseEntity<VideoProjectResponse.RenderResponse> render(@PathVariable UUID projectId) {
        var render = renderService.start(projectId);
        return ResponseEntity.accepted().body(VideoProjectResponse.renderResponse(render));
    }

    @GetMapping("/projects/{projectId}/render/content")
    public ResponseEntity<FileSystemResource> renderContent(@PathVariable UUID projectId) {
        var content = renderService.content(projectId);
        var headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("fire-" + projectId + ".mp4")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.valueOf("video/mp4"))
                .contentLength(content.path().toFile().length())
                .body(new FileSystemResource(content.path()));
    }

    @GetMapping("/projects/{projectId}/scenes/{sceneId}/generated")
    public ResponseEntity<FileSystemResource> generatedVideoContent(
            @PathVariable UUID projectId,
            @PathVariable UUID sceneId) {
        var path = service.generatedVideoContent(projectId, sceneId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .contentLength(path.toFile().length())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/providers")
    public Map<String, List<String>> providers() {
        return Map.of("providers", service.providers());
    }

    private VideoProjectResponse response(VideoProject project) {
        return VideoProjectResponse.from(
                project,
                service.assets(project.getId()),
                renderService.find(project.getId()));
    }
}
