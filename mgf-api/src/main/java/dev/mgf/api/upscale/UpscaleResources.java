package dev.mgf.api.upscale;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.BorrowedImage;

/** Input, output, and optional temporal images for one upscale call. */
public record UpscaleResources(
        BorrowedImage inputColor,
        BorrowedImage outputColor,
        Optional<BorrowedImage> depth,
        Optional<BorrowedImage> motionVectors,
        Optional<BorrowedImage> exposure,
        Optional<BorrowedImage> reactiveMask,
        Optional<BorrowedImage> transparencyMask) {

    public UpscaleResources {
        inputColor = Objects.requireNonNull(inputColor, "inputColor");
        outputColor = Objects.requireNonNull(outputColor, "outputColor");
        depth = Objects.requireNonNull(depth, "depth");
        motionVectors = Objects.requireNonNull(motionVectors, "motionVectors");
        exposure = Objects.requireNonNull(exposure, "exposure");
        reactiveMask = Objects.requireNonNull(reactiveMask, "reactiveMask");
        transparencyMask = Objects.requireNonNull(transparencyMask, "transparencyMask");
    }
}
