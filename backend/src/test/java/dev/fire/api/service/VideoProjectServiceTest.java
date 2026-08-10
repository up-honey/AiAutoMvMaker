package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VideoProjectServiceTest {

    @Test
    void requiresExplicitConfirmationBeforeAnyPaidProviderCanStart() {
        var store = mock(VideoProjectStore.class);
        var orchestrator = mock(VideoGenerationOrchestrator.class);
        org.mockito.Mockito.when(orchestrator.supports("gemini")).thenReturn(true);
        var service = new VideoProjectService(
                store,
                orchestrator,
                mock(MediaAssetStore.class),
                mock(MediaStorageService.class),
                mock(GeneratedVideoStorageService.class),
                "mock");

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), "gemini", false))
                .isInstanceOf(PaidGenerationConfirmationRequiredException.class)
                .hasMessageContaining("gemini");
        org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                .queueForGeneration(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
