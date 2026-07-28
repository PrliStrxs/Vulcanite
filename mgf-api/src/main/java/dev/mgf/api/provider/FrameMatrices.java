package dev.mgf.api.provider;

import java.util.Objects;

/** Current and previous frame transforms for temporal providers. */
public record FrameMatrices(
        Matrix4 currentViewProjection,
        Matrix4 previousViewProjection,
        Matrix4 currentInverseViewProjection,
        Matrix4 previousInverseViewProjection) {

    public FrameMatrices {
        currentViewProjection = Objects.requireNonNull(currentViewProjection, "currentViewProjection");
        previousViewProjection = Objects.requireNonNull(previousViewProjection, "previousViewProjection");
        currentInverseViewProjection = Objects.requireNonNull(currentInverseViewProjection, "currentInverseViewProjection");
        previousInverseViewProjection = Objects.requireNonNull(previousInverseViewProjection, "previousInverseViewProjection");
    }
}
