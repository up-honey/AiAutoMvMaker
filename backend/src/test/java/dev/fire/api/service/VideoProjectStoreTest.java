package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.fire.api.domain.ProjectStatus;
import dev.fire.api.domain.SceneStatus;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fire-store;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class VideoProjectStoreTest {

    @Autowired
    private VideoProjectStore store;

    @Autowired
    private ProjectRenderStore renderStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM project_renders");
        jdbcTemplate.update("DELETE FROM media_assets");
        jdbcTemplate.update("DELETE FROM video_scenes");
        jdbcTemplate.update("DELETE FROM video_projects");
    }

    @Test
    void persistsAndRestoresTheWholeProjectAggregate() {
        var project = project();
        store.save(project);

        var restored = store.getRequired(project.getId());

        assertThat(restored.getTitle()).isEqualTo("Persistent demo");
        assertThat(restored.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(restored.getScenes()).hasSize(2);
        assertThat(restored.getScenes().getFirst().getPrompt()).isEqualTo("Opening scene");
        assertThat(store.findAll()).extracting(VideoProject::getId).containsExactly(project.getId());
    }

    @Test
    void atomicallyQueuesAProjectOnlyOnce() {
        var project = store.save(project());

        var queued = store.queueForGeneration(project.getId(), "mock");

        assertThat(queued.getStatus()).isEqualTo(ProjectStatus.QUEUED);
        assertThat(queued.getProviderName()).isEqualTo("mock");
        assertThatThrownBy(() -> store.queueForGeneration(project.getId(), "mock"))
                .isInstanceOf(InvalidProjectStateException.class);
    }

    @Test
    void savesSceneProgressAndFindsRecoverableProjects() {
        var project = project();
        project.queue("mock");
        project.markProcessing();
        project.getScenes().getFirst().markProcessing();
        store.save(project);

        var recoverable = store.findRecoverable();

        assertThat(recoverable).hasSize(1);
        assertThat(recoverable.getFirst().getScenes().getFirst().getStatus())
                .isEqualTo(SceneStatus.PROCESSING);
    }

    @Test
    void persistsRenderStateAndPreventsDuplicateQueueing() {
        var project = store.save(project());
        var render = renderStore.queue(project.getId());
        render.markProcessing();
        renderStore.save(render);

        assertThat(renderStore.findRecoverable()).extracting(item -> item.getProjectId())
                .containsExactly(project.getId());
        assertThatThrownBy(() -> renderStore.queue(project.getId()))
                .isInstanceOf(InvalidRenderStateException.class);
    }

    private VideoProject project() {
        return new VideoProject(
                "Persistent demo",
                "A reliable video pipeline",
                "cinematic",
                "9:16",
                List.of(
                        new VideoScene(1, "Opening scene"),
                        new VideoScene(2, "Closing scene")));
    }
}
