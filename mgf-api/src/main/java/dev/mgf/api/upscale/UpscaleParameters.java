package dev.mgf.api.upscale;

import java.util.Objects;
import java.util.Optional;

/** Quality selection and optional live camera data for one upscale call. */
public record UpscaleParameters(
        Optional<UpscaleCameraParameters> camera,
        String qualityMode,
        JitterSequence jitter,
        TemporalUpscalingHints temporalHints) {

    public UpscaleParameters {
        camera = Objects.requireNonNull(camera, "camera");
        qualityMode = Objects.requireNonNull(qualityMode, "qualityMode");
        if (qualityMode.isBlank()) {
            throw new IllegalArgumentException("qualityMode must not be blank");
        }
        jitter = Objects.requireNonNull(jitter, "jitter");
        temporalHints = Objects.requireNonNull(temporalHints, "temporalHints");
    }

    public UpscaleParameters(
            Optional<UpscaleCameraParameters> camera,
            String qualityMode) {
        this(camera, qualityMode, JitterSequence.none(), TemporalUpscalingHints.neutral());
    }
}
