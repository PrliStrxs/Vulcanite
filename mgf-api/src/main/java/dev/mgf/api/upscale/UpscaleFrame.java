package dev.mgf.api.upscale;

import java.util.Objects;

import dev.mgf.api.provider.CommandRecordingContext;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameInfo;

/** Complete callback context for one upscaler dispatch. */
public record UpscaleFrame(
        FrameInfo frameInfo,
        FrameDimensions dimensions,
        CommandRecordingContext command,
        UpscaleResources resources,
        UpscaleParameters parameters) {

    public UpscaleFrame {
        frameInfo = Objects.requireNonNull(frameInfo, "frameInfo");
        dimensions = Objects.requireNonNull(dimensions, "dimensions");
        command = Objects.requireNonNull(command, "command");
        resources = Objects.requireNonNull(resources, "resources");
        parameters = Objects.requireNonNull(parameters, "parameters");
    }
}
