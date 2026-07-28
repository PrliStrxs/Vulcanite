package dev.mgf.api.upscale;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.FrameMatrices;

/** Numeric and temporal parameters for one upscale call. */
public record UpscaleParameters(
        Optional<FrameMatrices> matrices,
        float jitterX,
        float jitterY,
        float nearPlane,
        float farPlane,
        float verticalFieldOfViewDegrees,
        String qualityMode) {

    public UpscaleParameters {
        matrices = Objects.requireNonNull(matrices, "matrices");
        if (!Float.isFinite(jitterX) || !Float.isFinite(jitterY)
                || !Float.isFinite(nearPlane) || !Float.isFinite(farPlane)
                || !Float.isFinite(verticalFieldOfViewDegrees)
                || nearPlane < 0 || farPlane <= nearPlane
                || verticalFieldOfViewDegrees <= 0 || verticalFieldOfViewDegrees >= 180) {
            throw new IllegalArgumentException("invalid upscale numeric parameters");
        }
        qualityMode = Objects.requireNonNull(qualityMode, "qualityMode");
        if (qualityMode.isBlank()) {
            throw new IllegalArgumentException("qualityMode must not be blank");
        }
    }
}
