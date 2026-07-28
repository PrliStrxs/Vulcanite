package dev.mgf.api.present;

import java.util.List;

/** Ordered real-only or generated-then-real presentation batch. */
public record PresentBatch(List<PresentFrame> frames) {

    public PresentBatch {
        frames = List.copyOf(frames);
        if (frames.isEmpty() || frames.size() > 2) {
            throw new IllegalArgumentException("present batch must contain one or two frames");
        }
        for (int index = 0; index < frames.size(); index++) {
            if (frames.get(index).ordinal() != index) {
                throw new IllegalArgumentException("present ordinals must be contiguous from zero");
            }
        }
        if (frames.getLast().kind() != PresentFrameKind.REAL) {
            throw new IllegalArgumentException("real frame must be last");
        }
        if (frames.size() == 2 && frames.getFirst().kind() != PresentFrameKind.GENERATED) {
            throw new IllegalArgumentException("two-frame batch must be generated then real");
        }
    }
}
