package dev.fire.api.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.fire.api.service.VideoProjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VideoProjectController {

    private final VideoProjectService service;

    public VideoProjectController(VideoProjectService service) {
        this.service = service;
    }

    @PostMapping("/projects")
    public ResponseEntity<VideoProjectResponse> create(@Valid @RequestBody CreateVideoProjectRequest request) {
        var project = service.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(VideoProjectResponse.from(project));
    }

    @GetMapping("/projects")
    public List<VideoProjectResponse> list() {
        return service.list().stream().map(VideoProjectResponse::from).toList();
    }

    @GetMapping("/projects/{projectId}")
    public VideoProjectResponse get(@PathVariable UUID projectId) {
        return VideoProjectResponse.from(service.get(projectId));
    }

    @PostMapping("/projects/{projectId}/generate")
    public ResponseEntity<VideoProjectResponse> generate(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String provider) {
        return ResponseEntity.accepted().body(VideoProjectResponse.from(service.start(projectId, provider)));
    }

    @GetMapping("/providers")
    public Map<String, List<String>> providers() {
        return Map.of("providers", service.providers());
    }
}
