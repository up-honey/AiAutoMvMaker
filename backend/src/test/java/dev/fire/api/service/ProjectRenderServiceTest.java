package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import dev.fire.api.domain.ProjectRender;
import dev.fire.api.domain.VideoProject;
import dev.fire.api.domain.VideoScene;
import org.junit.jupiter.api.Test;

class ProjectRenderServiceTest {

    @Test
    void allowsFinalRenderingFromGeneratedScenesWithoutUploadedVisuals() {
        var projectStore = mock(VideoProjectStore.class);
        var assetStore = mock(MediaAssetStore.class);
        var renderStore = mock(ProjectRenderStore.class);
        var worker = mock(ProjectRenderWorker.class);
        var generatedStorage = mock(GeneratedVideoStorageService.class);
        var project = new VideoProject(
                "Generated-only render",
                "Prompt to final video",
                "cinematic",
                "9:16",
                List.of(new VideoScene(1, "A calm opening")));
        var scene = project.getScenes().getFirst();
        project.queue("gemini");
        project.markProcessing();
        scene.markSubmitted("job-1", "gemini-omni-flash-preview", new BigDecimal("1.0000"));
        scene.markCompleted(
                "job-1",
                "/api/projects/" + project.getId() + "/scenes/" + scene.getId() + "/generated");
        project.markCompleted();
        var render = new ProjectRender(project.getId());

        when(projectStore.getRequired(project.getId())).thenReturn(project);
        when(assetStore.findVisualsByProject(project.getId())).thenReturn(List.of());
        when(generatedStorage.exists(project.getId(), scene.getId())).thenReturn(true);
        when(renderStore.queue(project.getId())).thenReturn(render);

        var service = new ProjectRenderService(
                projectStore,
                assetStore,
                renderStore,
                worker,
                mock(MediaStorageService.class),
                generatedStorage);

        assertThat(service.start(project.getId())).isSameAs(render);
        verify(worker).render(project.getId());
    }
}
