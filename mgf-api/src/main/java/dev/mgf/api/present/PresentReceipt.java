package dev.mgf.api.present;

import java.util.Objects;

import dev.mgf.api.provider.FrameInfo;

/** Completion information delivered after MGF attempts one presentation. */
public record PresentReceipt(
        FrameInfo frameInfo,
        PresentFrameKind kind,
        int ordinal,
        boolean presented,
        long presentDurationNanos,
        String message) {

    public PresentReceipt {
        frameInfo = Objects.requireNonNull(frameInfo, "frameInfo");
        kind = Objects.requireNonNull(kind, "kind");
        if (ordinal < 0 || ordinal > 1 || presentDurationNanos < 0) {
            throw new IllegalArgumentException("invalid present ordinal or duration");
        }
        message = Objects.requireNonNull(message, "message");
        if (!presented && message.isBlank()) {
            throw new IllegalArgumentException("failed presentation requires a message");
        }
    }
}
