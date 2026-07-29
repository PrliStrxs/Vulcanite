package dev.mgf.api.upscale;

import java.util.HashSet;
import java.util.Set;

import dev.mgf.api.provider.ColorEncoding;

/** Supported scale range, color encodings, and quality modes. */
public record UpscalerCapabilities(
        double minimumScale,
        double maximumScale,
        Set<Double> renderScales,
        Set<ColorEncoding> colorEncodings,
        Set<String> qualityModes) {

    public UpscalerCapabilities {
        if (!Double.isFinite(minimumScale) || !Double.isFinite(maximumScale)
                || minimumScale <= 0 || maximumScale < minimumScale) {
            throw new IllegalArgumentException("invalid scale range");
        }
        renderScales = Set.copyOf(renderScales);
        colorEncodings = Set.copyOf(colorEncodings);
        qualityModes = Set.copyOf(qualityModes);
        if (renderScales.isEmpty() || colorEncodings.isEmpty() || qualityModes.isEmpty()
                || qualityModes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("render scales, color encodings, and quality modes must not be empty");
        }
        for (double scale : renderScales) {
            if (!Double.isFinite(scale) || scale <= 0.0
                    || scale < minimumScale || scale > maximumScale) {
                throw new IllegalArgumentException("render scale outside supported range: " + scale);
            }
        }
    }

    public UpscalerCapabilities(
            double minimumScale,
            double maximumScale,
            Set<ColorEncoding> colorEncodings,
            Set<String> qualityModes) {
        this(minimumScale, maximumScale,
                defaultRenderScales(minimumScale, maximumScale), colorEncodings, qualityModes);
    }

    private static Set<Double> defaultRenderScales(double minimumScale, double maximumScale) {
        Set<Double> scales = new HashSet<>();
        for (double scale : new double[] {0.5, 2.0 / 3.0, 0.75, 1.0}) {
            if (scale >= minimumScale && scale <= maximumScale) {
                scales.add(scale);
            }
        }
        if (scales.isEmpty() && Double.isFinite(minimumScale) && Double.isFinite(maximumScale)
                && minimumScale > 0.0 && maximumScale >= minimumScale) {
            scales.add(minimumScale);
        }
        return Set.copyOf(scales);
    }
}
