package dev.fire.api.provider;

import java.math.BigDecimal;

public interface VideoProvider {

    String name();

    default String model() {
        return name();
    }

    default BigDecimal estimatedCostUsd(VideoGenerationCommand command) {
        return BigDecimal.ZERO;
    }

    VideoGenerationSubmission submit(VideoGenerationCommand command);

    VideoGenerationResult awaitResult(VideoGenerationCommand command, String providerJobId);
}
