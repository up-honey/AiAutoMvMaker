package dev.fire.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProjectRecoveryService implements ApplicationRunner {

    private final VideoProjectStore store;
    private final VideoGenerationOrchestrator orchestrator;
    private final String defaultProvider;

    public ProjectRecoveryService(
            VideoProjectStore store,
            VideoGenerationOrchestrator orchestrator,
            @Value("${fire.video.default-provider:mock}") String defaultProvider) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (var project : store.findRecoverable()) {
            project.prepareForRecovery(defaultProvider);
            store.save(project);
            orchestrator.generate(project.getId(), project.getProviderName());
        }
    }
}
