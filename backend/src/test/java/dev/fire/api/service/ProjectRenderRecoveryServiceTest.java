package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import dev.fire.api.domain.ProjectRender;
import dev.fire.api.domain.RenderStatus;
import org.junit.jupiter.api.Test;

class ProjectRenderRecoveryServiceTest {

    @Test
    void requeuesAndRestartsAnInterruptedRender() {
        var store = mock(ProjectRenderStore.class);
        var worker = mock(ProjectRenderWorker.class);
        var render = new ProjectRender(UUID.randomUUID());
        render.markProcessing();
        when(store.findRecoverable()).thenReturn(List.of(render));

        new ProjectRenderRecoveryService(store, worker).run(null);

        assertThat(render.getStatus()).isEqualTo(RenderStatus.QUEUED);
        verify(store).save(render);
        verify(worker).render(render.getProjectId());
    }
}
