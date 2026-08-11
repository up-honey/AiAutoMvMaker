package dev.fire.api.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.List;

import org.junit.jupiter.api.Test;

class MockVideoProviderTest {

    private final MockVideoProvider provider = new MockVideoProvider();

    @Test
    void returnsAStableResultShapeWithoutCallingAPaidProvider() {
        var command = command("A calm opening shot");
        var submission = provider.submit(command);
        var result = provider.awaitResult(command, submission.providerJobId());

        assertThat(result.providerJobId()).startsWith("mock-");
        assertThat(result.previewUri()).startsWith("mock://renders/");
    }

    @Test
    void supportsADeterministicFailureFixture() {
        assertThatThrownBy(() -> provider.submit(command("[fail] rejected scene")))
                .isInstanceOf(VideoProviderException.class)
                .hasMessage("MOCK_PROVIDER_REJECTED");
    }

    private VideoGenerationCommand command(String prompt) {
        return new VideoGenerationCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                prompt,
                "cinematic",
                "9:16",
                List.of(),
                null);
    }
}
