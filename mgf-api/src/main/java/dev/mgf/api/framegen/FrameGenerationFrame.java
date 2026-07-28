package dev.mgf.api.framegen;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.CommandRecordingContext;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameInfo;
import dev.mgf.api.provider.FrameMatrices;

/** Complete callback context for one frame-generation dispatch. */
public record FrameGenerationFrame(
        FrameInfo frameInfo,
        FrameDimensions dimensions,
        CommandRecordingContext command,
        FrameGenerationResources resources,
        Optional<FrameMatrices> matrices) {

    public FrameGenerationFrame {
        frameInfo = Objects.requireNonNull(frameInfo, "frameInfo");
        dimensions = Objects.requireNonNull(dimensions, "dimensions");
        command = Objects.requireNonNull(command, "command");
        resources = Objects.requireNonNull(resources, "resources");
        matrices = Objects.requireNonNull(matrices, "matrices");
    }
}
