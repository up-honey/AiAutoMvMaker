package dev.fire.api.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProjectRenderRecoveryService implements ApplicationRunner {

    private final ProjectRenderStore renderStore;
    private final ProjectRenderWorker worker;

    public ProjectRenderRecoveryService(ProjectRenderStore renderStore, ProjectRenderWorker worker) {
        this.renderStore = renderStore;
        this.worker = worker;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (var render : renderStore.findRecoverable()) {
            render.prepareForRecovery();
            renderStore.save(render);
            worker.render(render.getProjectId());
        }
    }
}
