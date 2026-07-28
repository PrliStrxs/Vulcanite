package dev.mgf.api.provider;

import java.util.Objects;

/** Opaque native image and view borrowed for a bounded provider lifetime. */
public record BorrowedImage(
        long imageHandle,
        long imageViewHandle,
        int width,
        int height,
        int nativeFormat,
        long nativeUsageMask,
        ColorEncoding colorEncoding,
        ImageState state,
        ImageOwnership ownership,
        ImageLifetime lifetime,
        long deviceGeneration,
        long resourceGeneration) {

    public BorrowedImage {
        if (imageHandle == 0 || imageViewHandle == 0) {
            throw new IllegalArgumentException("image and view handles must be non-zero");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        if (nativeFormat < 1 || nativeUsageMask < 0) {
            throw new IllegalArgumentException("invalid native format or usage");
        }
        colorEncoding = Objects.requireNonNull(colorEncoding, "colorEncoding");
        state = Objects.requireNonNull(state, "state");
        ownership = Objects.requireNonNull(ownership, "ownership");
        lifetime = Objects.requireNonNull(lifetime, "lifetime");
        if (deviceGeneration < 1 || resourceGeneration < 1) {
            throw new IllegalArgumentException("generations must be positive");
        }
    }
}
