package dev.mgf.api.upscale;

import java.util.Objects;

/** Motion-vector interpretation for a verified motion-vector input resource. */
public record MotionVectorConvention(
        MotionVectorUnits units,
        MotionVectorDirection direction,
        MotionVectorYAxis yAxis,
        float scaleX,
        float scaleY) {

    public MotionVectorConvention {
        units = Objects.requireNonNull(units, "units");
        direction = Objects.requireNonNull(direction, "direction");
        yAxis = Objects.requireNonNull(yAxis, "yAxis");
        if (!Float.isFinite(scaleX) || !Float.isFinite(scaleY)
                || scaleX == 0.0F || scaleY == 0.0F) {
            throw new IllegalArgumentException("motion-vector scales must be finite and non-zero");
        }
    }

    public static MotionVectorConvention normalizedCurrentToPreviousYDown() {
        return new MotionVectorConvention(
                MotionVectorUnits.NORMALIZED_RENDER_SIZE,
                MotionVectorDirection.CURRENT_TO_PREVIOUS,
                MotionVectorYAxis.DOWN,
                1.0F,
                1.0F);
    }
}
