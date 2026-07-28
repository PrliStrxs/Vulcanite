package dev.mgf.api.framegen;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.BorrowedImage;

/** Real history, output, and optional temporal images for frame generation. */
public record FrameGenerationResources(
        BorrowedImage currentReal,
        Optional<BorrowedImage> previousReal,
        BorrowedImage generatedOutput,
        Optional<BorrowedImage> depth,
        Optional<BorrowedImage> motionVectors,
        Optional<BorrowedImage> opticalFlow,
        Optional<BorrowedImage> uiMask) {

    public FrameGenerationResources {
        currentReal = Objects.requireNonNull(currentReal, "currentReal");
        previousReal = Objects.requireNonNull(previousReal, "previousReal");
        generatedOutput = Objects.requireNonNull(generatedOutput, "generatedOutput");
        depth = Objects.requireNonNull(depth, "depth");
        motionVectors = Objects.requireNonNull(motionVectors, "motionVectors");
        opticalFlow = Objects.requireNonNull(opticalFlow, "opticalFlow");
        uiMask = Objects.requireNonNull(uiMask, "uiMask");
    }
}
