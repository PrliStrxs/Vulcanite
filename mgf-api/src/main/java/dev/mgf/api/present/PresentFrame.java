package dev.mgf.api.present;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.BorrowedImage;
import dev.mgf.api.provider.CommandRecordingContext;
import dev.mgf.api.provider.FrameInfo;

/** One image in an MGF-owned presentation batch. */
public record PresentFrame(
        FrameInfo frameInfo,
        PresentFrameKind kind,
        int ordinal,
        BorrowedImage source,
        Optional<CommandRecordingContext> command) {

    public PresentFrame {
        frameInfo = Objects.requireNonNull(frameInfo, "frameInfo");
        kind = Objects.requireNonNull(kind, "kind");
        if (ordinal < 0 || ordinal > 1) {
            throw new IllegalArgumentException("ordinal must be zero or one");
        }
        source = Objects.requireNonNull(source, "source");
        command = Objects.requireNonNull(command, "command");
    }
}
