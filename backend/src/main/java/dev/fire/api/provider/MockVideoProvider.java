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
    public VideoGenerationResult generate(VideoGenerationCommand command) {
        if (command.prompt().toLowerCase(Locale.ROOT).contains("[fail]")) {
            throw new VideoProviderException("MOCK_PROVIDER_REJECTED");
        }

        try {
            Thread.sleep(300);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VideoProviderException("GENERATION_INTERRUPTED");
        }

        var jobId = "mock-" + UUID.randomUUID();
        return new VideoGenerationResult(jobId, "mock://renders/" + jobId + ".mp4");
    }
}
