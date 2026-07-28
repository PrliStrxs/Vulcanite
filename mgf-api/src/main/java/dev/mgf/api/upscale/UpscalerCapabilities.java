package dev.mgf.api.upscale;

import java.util.Set;

import dev.mgf.api.provider.ColorEncoding;

/** Supported scale range, color encodings, and quality modes. */
public record UpscalerCapabilities(
        double minimumScale,
        double maximumScale,
        Set<ColorEncoding> colorEncodings,
        Set<String> qualityModes) {

    public UpscalerCapabilities {
        if (!Double.isFinite(minimumScale) || !Double.isFinite(maximumScale)
                || minimumScale <= 0 || maximumScale < minimumScale) {
            throw new IllegalArgumentException("invalid scale range");
        }
        colorEncodings = Set.copyOf(colorEncodings);
        qualityModes = Set.copyOf(qualityModes);
        if (colorEncodings.isEmpty() || qualityModes.isEmpty()
                || qualityModes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("color encodings and quality modes must not be empty");
        }
    }
}
