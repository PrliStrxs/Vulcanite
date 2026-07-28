package dev.mgf.api.upscale;

import java.util.Objects;

import dev.mgf.api.provider.FrameMatrices;

/** Camera and temporal parameters supplied only when the adapter has live data. */
public record UpscaleCameraParameters(
        FrameMatrices matrices,
        float jitterX,
        float jitterY,
        float nearPlane,
        float farPlane,
        float verticalFieldOfViewDegrees) {

    public UpscaleCameraParameters {
        matrices = Objects.requireNonNull(matrices, "matrices");
        if (!Float.isFinite(jitterX) || !Float.isFinite(jitterY)
                || !Float.isFinite(nearPlane) || !Float.isFinite(farPlane)
                || !Float.isFinite(verticalFieldOfViewDegrees)
                || nearPlane < 0 || farPlane <= nearPlane
                || verticalFieldOfViewDegrees <= 0 || verticalFieldOfViewDegrees >= 180) {
            throw new IllegalArgumentException("invalid upscale camera parameters");
        }
    }
}
