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
        Optional<BorrowedImage> transparencyMask,
        Optional<BorrowedImage> uiMask,
        Optional<DepthConvention> depthConvention,
        Optional<MotionVectorConvention> motionVectorConvention) {

    public UpscaleResources {
        inputColor = Objects.requireNonNull(inputColor, "inputColor");
        outputColor = Objects.requireNonNull(outputColor, "outputColor");
        depth = Objects.requireNonNull(depth, "depth");
        motionVectors = Objects.requireNonNull(motionVectors, "motionVectors");
        exposure = Objects.requireNonNull(exposure, "exposure");
        reactiveMask = Objects.requireNonNull(reactiveMask, "reactiveMask");
        transparencyMask = Objects.requireNonNull(transparencyMask, "transparencyMask");
        uiMask = Objects.requireNonNull(uiMask, "uiMask");
        depthConvention = Objects.requireNonNull(depthConvention, "depthConvention");
        motionVectorConvention = Objects.requireNonNull(motionVectorConvention, "motionVectorConvention");
        if (depth.isPresent() != depthConvention.isPresent()) {
            throw new IllegalArgumentException("depth image and convention must be supplied together");
        }
        if (motionVectors.isPresent() != motionVectorConvention.isPresent()) {
            throw new IllegalArgumentException("motion-vector image and convention must be supplied together");
        }
    }

    public UpscaleResources(
            BorrowedImage inputColor,
            BorrowedImage outputColor,
            Optional<BorrowedImage> depth,
            Optional<BorrowedImage> motionVectors,
            Optional<BorrowedImage> exposure,
            Optional<BorrowedImage> reactiveMask,
            Optional<BorrowedImage> transparencyMask) {
        this(inputColor, outputColor, depth, motionVectors, exposure, reactiveMask, transparencyMask,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
