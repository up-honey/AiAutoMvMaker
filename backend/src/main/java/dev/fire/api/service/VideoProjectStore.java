package dev.fire.api.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.fire.api.domain.VideoProject;
import org.springframework.stereotype.Component;

@Component
public class VideoProjectStore {

    private final ConcurrentHashMap<UUID, VideoProject> projects = new ConcurrentHashMap<>();

    public VideoProject save(VideoProject project) {
        projects.put(project.getId(), project);
        return project;
    }

    public VideoProject getRequired(UUID projectId) {
        var project = projects.get(projectId);
        if (project == null) {
            throw new ProjectNotFoundException(projectId);
        }
        return project;
    }

    public List<VideoProject> findAll() {
        return projects.values().stream()
                .sorted(Comparator.comparing(VideoProject::getCreatedAt).reversed())
                .toList();
    }
}
