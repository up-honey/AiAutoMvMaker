package dev.fire.api.service;

import java.util.UUID;

public class GeneratedVideoNotFoundException extends RuntimeException {

    public GeneratedVideoNotFoundException(UUID sceneId) {
        super("Generated video was not found for scene " + sceneId);
    }
}
