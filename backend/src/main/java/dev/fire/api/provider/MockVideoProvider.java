package dev.fire.api.provider;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class MockVideoProvider implements VideoProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public VideoGenerationSubmission submit(VideoGenerationCommand command) {
        if (command.prompt().toLowerCase(Locale.ROOT).contains("[fail]")) {
            throw new VideoProviderException("MOCK_PROVIDER_REJECTED");
        }

        return new VideoGenerationSubmission("mock-" + UUID.randomUUID());
    }

    @Override
    public VideoGenerationResult awaitResult(VideoGenerationCommand command, String providerJobId) {

        try {
            Thread.sleep(300);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("GENERATION_INTERRUPTED");
        }

        return new VideoGenerationResult(providerJobId, "mock://renders/" + providerJobId + ".mp4");
    }
}
