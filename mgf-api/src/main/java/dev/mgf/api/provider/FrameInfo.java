package dev.mgf.api.provider;

/** Identity, timing, and lifetime generations for one rendered frame. */
public record FrameInfo(
        long frameId,
        double deltaSeconds,
        boolean historyReset,
        long deviceGeneration,
        long resourceGeneration) {

    public FrameInfo {
        if (frameId < 0) {
            throw new IllegalArgumentException("frameId must be non-negative");
        }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
        if (deviceGeneration < 1 || resourceGeneration < 1) {
            throw new IllegalArgumentException("generations must be positive");
        }
    }
}
