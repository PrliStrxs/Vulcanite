package dev.mgf.api.upscale;

import java.util.Objects;

/** Non-image temporal metadata supplied with one upscale dispatch. */
public record TemporalUpscalingHints(
        boolean resetHistory,
        float mipBias,
        float sharpness,
        ExposureMode exposureMode,
        UiCompositionHint uiCompositionHint) {

    public TemporalUpscalingHints {
        if (!Float.isFinite(mipBias) || !Float.isFinite(sharpness)
                || sharpness < 0.0F || sharpness > 1.0F) {
            throw new IllegalArgumentException("invalid temporal upscaling hints");
        }
        exposureMode = Objects.requireNonNull(exposureMode, "exposureMode");
        uiCompositionHint = Objects.requireNonNull(uiCompositionHint, "uiCompositionHint");
    }

    public static TemporalUpscalingHints neutral() {
        return new TemporalUpscalingHints(
                false, 0.0F, 0.0F, ExposureMode.IDENTITY, UiCompositionHint.UNKNOWN);
    }
}
