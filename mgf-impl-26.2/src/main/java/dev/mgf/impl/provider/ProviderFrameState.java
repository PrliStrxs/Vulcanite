package dev.mgf.impl.provider;

import java.util.Objects;

import dev.mgf.api.provider.FrameDimensions;

/** Tracks live adapter generations independently of optional provider outputs. */
final class ProviderFrameState {

    private long deviceGeneration;
    private long resourceGeneration;
    private FrameDimensions dimensions;

    void openDevice(long generation) {
        if (generation < 1) {
            throw new IllegalArgumentException("device generation must be positive");
        }
        deviceGeneration = generation;
        resourceGeneration = 0L;
        dimensions = null;
    }

    Snapshot beginFrame(long liveDeviceGeneration, FrameDimensions liveDimensions) {
        requireDevice(liveDeviceGeneration);
        Objects.requireNonNull(liveDimensions, "liveDimensions");
        boolean resized = !liveDimensions.equals(dimensions);
        if (resized) {
            resourceGeneration = Math.addExact(resourceGeneration, 1L);
            dimensions = liveDimensions;
        }
        if (resourceGeneration < 1) {
            throw new IllegalStateException("frame dimensions have no live resource generation");
        }
        return new Snapshot(resourceGeneration, resized);
    }

    void validateCurrent(long candidateDeviceGeneration, long candidateResourceGeneration) {
        requireDevice(candidateDeviceGeneration);
        if (candidateResourceGeneration != resourceGeneration) {
            throw new IllegalStateException(
                    "stale resource generation " + candidateResourceGeneration
                            + ", current is " + resourceGeneration);
        }
    }

    void closeDevice() {
        deviceGeneration = 0L;
        resourceGeneration = 0L;
        dimensions = null;
    }

    private void requireDevice(long candidateDeviceGeneration) {
        if (deviceGeneration < 1 || candidateDeviceGeneration != deviceGeneration) {
            throw new IllegalStateException(
                    "stale device generation " + candidateDeviceGeneration
                            + ", current is " + deviceGeneration);
        }
    }

    record Snapshot(long resourceGeneration, boolean resized) {
    }
}
