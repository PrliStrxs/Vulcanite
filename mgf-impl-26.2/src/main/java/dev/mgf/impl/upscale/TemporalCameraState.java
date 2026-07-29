package dev.mgf.impl.upscale;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.FrameMatrices;
import dev.mgf.api.provider.ResetReason;

/** Tracks current and previous camera matrices as one temporal unit. */
public final class TemporalCameraState {

    private FrameMatrices previous;
    private FrameMatrices current;
    private ResetReason lastResetReason = ResetReason.FIRST_FRAME;

    public Optional<FrameMatrices> update(FrameMatrices matrices) {
        Objects.requireNonNull(matrices, "matrices");
        previous = current;
        current = matrices;
        return previous == null ? Optional.empty() : Optional.of(matrices);
    }

    public void reset(ResetReason reason) {
        lastResetReason = Objects.requireNonNull(reason, "reason");
        previous = null;
        current = null;
    }

    public Optional<FrameMatrices> current() {
        return Optional.ofNullable(current);
    }

    public Optional<FrameMatrices> previous() {
        return Optional.ofNullable(previous);
    }

    public ResetReason lastResetReason() {
        return lastResetReason;
    }
}
