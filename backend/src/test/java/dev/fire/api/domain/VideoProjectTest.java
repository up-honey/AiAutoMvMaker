package dev.fire.api.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class VideoProjectTest {

    @Test
    void followsTheHappyPathStateTransitions() {
        var project = project();

        project.queue();
        project.markProcessing();
        project.getScenes().getFirst().markProcessing();
        project.getScenes().getFirst().markCompleted("job-1", "mock://job-1.mp4");
        project.markCompleted();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(project.getScenes().getFirst().getStatus()).isEqualTo(SceneStatus.COMPLETED);
    }

    @Test
    void rejectsStartingAProjectTwice() {
        var project = project();
        project.queue();

        assertThatThrownBy(project::queue)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROJECT_NOT_STARTABLE");
    }

    private VideoProject project() {
        return new VideoProject(
                "Demo",
                "A city at night",
                "cinematic",
                "9:16",
                List.of(new VideoScene(1, "Opening scene")));
    }
}
