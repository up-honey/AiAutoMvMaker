package dev.fire.api.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.fire.api.domain.AssetKind;
import dev.fire.api.domain.RenderPreset;
import org.springframework.stereotype.Component;

@Component
public class FfmpegCommandFactory {

    public List<String> build(
            String ffmpegExecutable,
            List<TimelineInput> timeline,
            Path soundtrack,
            RenderPreset preset,
            String aspectRatio,
            Path output) {
        if (timeline.isEmpty()) {
            throw new IllegalArgumentException("Timeline cannot be empty");
        }

        var command = new ArrayList<String>();
        command.add(ffmpegExecutable);
        command.addAll(List.of("-y", "-hide_banner", "-loglevel", "error"));

        var transitionSeconds = transitionSeconds(timeline);
        for (var input : timeline) {
            if ("image/gif".equals(input.contentType())) {
                command.addAll(List.of("-stream_loop", "-1"));
            } else if (input.kind() == AssetKind.IMAGE) {
                command.addAll(List.of("-loop", "1"));
            }
            command.addAll(List.of("-t", seconds(input.durationSeconds()), "-i", input.path().toString()));
        }
        if (soundtrack != null) {
            command.addAll(List.of("-stream_loop", "-1", "-i", soundtrack.toString()));
        }

        var dimensions = "9:16".equals(aspectRatio) ? new Dimensions(720, 1280) : new Dimensions(1280, 720);
        var filter = new StringBuilder();
        for (int index = 0; index < timeline.size(); index++) {
            var input = timeline.get(index);
            var pad = index < timeline.size() - 1 ? transitionSeconds : 0;
            var paddedDuration = input.durationSeconds() + pad;
            filter.append('[').append(index).append(":v]")
                    .append("scale=").append(dimensions.width()).append(':').append(dimensions.height())
                    .append(":force_original_aspect_ratio=increase,")
                    .append("crop=").append(dimensions.width()).append(':').append(dimensions.height()).append(',')
                    .append("setsar=1,fps=30,")
                    .append("trim=duration=").append(seconds(input.durationSeconds())).append(',')
                    .append("setpts=PTS-STARTPTS,");
            if (pad > 0) {
                filter.append("tpad=stop_mode=clone:stop_duration=").append(seconds(pad)).append(',')
                        .append("trim=duration=").append(seconds(paddedDuration)).append(',');
            }
            filter.append(colorFilter(preset))
                    .append("format=yuv420p,settb=AVTB[v").append(index).append("];\n");
        }

        if (timeline.size() == 1) {
            filter.append("[v0]null[outv]");
        } else {
            var currentLabel = "v0";
            var offset = timeline.getFirst().durationSeconds();
            for (int index = 1; index < timeline.size(); index++) {
                var outputLabel = index == timeline.size() - 1 ? "outv" : "x" + index;
                filter.append('[').append(currentLabel).append("][v").append(index).append(']')
                        .append("xfade=transition=fade:duration=").append(seconds(transitionSeconds))
                        .append(":offset=").append(seconds(offset))
                        .append('[').append(outputLabel).append(']');
                if (index < timeline.size() - 1) {
                    filter.append(";\n");
                }
                currentLabel = outputLabel;
                offset += timeline.get(index).durationSeconds();
            }
        }

        command.addAll(List.of("-filter_complex", filter.toString(), "-map", "[outv]"));
        if (soundtrack != null) {
            command.addAll(List.of(
                    "-map", timeline.size() + ":a:0",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-af", "afade=t=in:st=0:d=1",
                    "-shortest"));
        } else {
            command.add("-an");
        }
        command.addAll(List.of(
                "-c:v", "libx264",
                "-preset", "medium",
                "-crf", "20",
                "-r", "30",
                "-movflags", "+faststart",
                output.toString()));
        return List.copyOf(command);
    }

    private double transitionSeconds(List<TimelineInput> timeline) {
        if (timeline.size() == 1) {
            return 0;
        }
        var shortest = timeline.stream().mapToDouble(TimelineInput::durationSeconds).min().orElse(0.5);
        return Math.max(0.04, Math.min(0.25, shortest / 2.5));
    }

    private String colorFilter(RenderPreset preset) {
        return switch (preset) {
            case CLEAN -> "eq=contrast=1.02:saturation=1.02,";
            case ROMANTIC -> "eq=contrast=1.03:saturation=1.10:brightness=0.025,colorbalance=rs=.045:bs=-.02,";
            case FAIRYTALE_PARK -> "eq=contrast=1.05:saturation=1.22:brightness=0.035,colorbalance=rs=.04:gs=.015:bs=.03,vignette=PI/9,";
            case CINEMATIC -> "eq=contrast=1.12:saturation=.92:brightness=-.01,colorbalance=bs=.035,";
        };
    }

    private String seconds(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public record TimelineInput(AssetKind kind, String contentType, Path path, double durationSeconds) {

        public TimelineInput {
            if (kind == AssetKind.AUDIO) {
                throw new IllegalArgumentException("Audio cannot be a visual timeline input");
            }
            if (durationSeconds < 0.1) {
                throw new IllegalArgumentException("Timeline input must be at least 0.1 seconds");
            }
        }
    }

    private record Dimensions(int width, int height) {
    }
}
