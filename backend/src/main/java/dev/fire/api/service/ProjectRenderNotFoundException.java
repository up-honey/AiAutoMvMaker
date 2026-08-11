package dev.fire.api.service;

import java.util.UUID;

public class ProjectRenderNotFoundException extends RuntimeException {

    public ProjectRenderNotFoundException(UUID projectId) {
        super("Render not found for project: " + projectId);
    }
}
