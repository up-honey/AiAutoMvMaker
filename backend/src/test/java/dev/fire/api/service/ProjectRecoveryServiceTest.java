package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.SceneStatus;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import org.junit.jupiter.api.Test;

class ProjectRecoveryServiceTest {

    @Test
    void resumesInterruptedProjectsWithoutRegeneratingCompletedScenes() {
        var store = mock(VideoProjectStore.class);
        var orchestrator = mock(VideoGenerationOrchestrator.class);
        var project = new VideoProject(
                "Recovery demo",
                "Resume after restart",
                "cinematic",
                "9:16",
                List.of(
                        new VideoScene(1, "Completed scene"),
                        new VideoScene(2, "Interrupted scene")));
        project.queue("mock");
        project.markProcessing();
        project.getScenes().getFirst().markProcessing();
        project.getScenes().getFirst().markCompleted("job-1", "mock://job-1.mp4");
        project.getScenes().get(1).markProcessing();
        when(store.findRecoverable()).thenReturn(List.of(project));

        var recovery = new ProjectRecoveryService(store, orchestrator, "mock");
        recovery.run(null);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.QUEUED);
        assertThat(project.getScenes().getFirst().getStatus()).isEqualTo(SceneStatus.COMPLETED);
        assertThat(project.getScenes().get(1).getStatus()).isEqualTo(SceneStatus.PENDING);
        verify(store).save(project);
        verify(orchestrator).generate(project.getId(), "mock");
    }
}
