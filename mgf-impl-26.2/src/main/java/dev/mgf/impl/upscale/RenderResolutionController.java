package dev.mgf.impl.upscale;

import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.upscale.UpscalerCapabilities;

/** Chooses adapter-supported render resolutions for temporal upscaler sessions. */
public final class RenderResolutionController {

    public static final double SCALE_50 = 0.5;
    public static final double SCALE_66 = 2.0 / 3.0;
    public static final double SCALE_75 = 0.75;
    public static final double SCALE_100 = 1.0;
    public static final Set<Double> SUPPORTED_SCALES = Set.of(SCALE_50, SCALE_66, SCALE_75, SCALE_100);

    private RenderResolutionController() {
    }

    public static OptionalDouble selectScale(UpscalerCapabilities capabilities, double preferredScale) {
        Objects.requireNonNull(capabilities, "capabilities");
        if (!Double.isFinite(preferredScale) || preferredScale <= 0.0) {
            throw new IllegalArgumentException("preferredScale must be finite and positive");
        }
        return capabilities.renderScales().stream()
                .filter(SUPPORTED_SCALES::contains)
                .filter(scale -> scale >= capabilities.minimumScale() && scale <= capabilities.maximumScale())
                .min(Comparator.comparingDouble(scale -> Math.abs(scale - preferredScale)))
                .map(OptionalDouble::of)
                .orElse(OptionalDouble.empty());
    }

    public static boolean supportsNativeScale(UpscalerCapabilities capabilities) {
        return selectScale(capabilities, SCALE_100).stream().anyMatch(scale -> scale == SCALE_100);
    }

    public static FrameDimensions dimensions(int displayWidth, int displayHeight, double scale) {
        if (displayWidth < 1 || displayHeight < 1 || !SUPPORTED_SCALES.contains(scale)) {
            throw new IllegalArgumentException("unsupported render dimensions");
        }
        int renderWidth = Math.max(1, (int) Math.round(displayWidth * scale));
        int renderHeight = Math.max(1, (int) Math.round(displayHeight * scale));
        return new FrameDimensions(renderWidth, renderHeight, displayWidth, displayHeight);
    }
}
