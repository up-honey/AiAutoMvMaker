package dev.fire.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.RenderPreset;
import org.junit.jupiter.api.Test;

class FfmpegCommandFactoryTest {

    private final FfmpegCommandFactory factory = new FfmpegCommandFactory();

    @Test
    void buildsAFiftySecondTimelineForOneHundredPhotosAtTwoPerSecond() {
        var timeline = IntStream.range(0, 100)
                .mapToObj(index -> new FfmpegCommandFactory.TimelineInput(
                        AssetKind.IMAGE,
                        "image/png",
                        Path.of("safe", "photo-" + index + ".png"),
                        0.5))
                .toList();

        var command = factory.build(
                "ffmpeg",
                timeline,
                Path.of("safe", "music.mp3"),
                RenderPreset.FAIRYTALE_PARK,
                "9:16",
                Path.of("safe", "output.mp4"));

        assertThat(command.stream().filter("-i"::equals).count()).isEqualTo(101);
        assertThat(command).containsSequence("-map", "100:a:0");
        var filter = command.get(command.indexOf("-filter_complex") + 1);
        assertThat(filter).contains("offset=49.500[outv]");
        assertThat(filter).contains("vignette=PI/9");
        assertThat(filter).doesNotContain("safe\\photo", "safe/photo");
    }

    @Test
    void keepsMediaPathsAsProcessArgumentsInsteadOfFilterOrShellText() {
        var suspiciousButValidPath = Path.of("safe", "wedding & memories (1).mp4");

        var command = factory.build(
                "ffmpeg",
                List.of(new FfmpegCommandFactory.TimelineInput(
                        AssetKind.VIDEO,
                        "video/mp4",
                        suspiciousButValidPath,
                        4.2)),
                null,
                RenderPreset.ROMANTIC,
                "16:9",
                Path.of("safe", "output.mp4"));

        assertThat(command).contains(suspiciousButValidPath.toString());
        assertThat(command).doesNotContain("cmd", "/c", "sh", "-c");
        assertThat(command.get(command.indexOf("-filter_complex") + 1)).doesNotContain("wedding", "memories");
    }
}
