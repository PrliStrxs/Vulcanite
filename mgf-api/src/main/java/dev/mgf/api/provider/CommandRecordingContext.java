package dev.mgf.api.provider;

/** Opaque native command buffer that is already recording and remains MGF-owned. */
public record CommandRecordingContext(
        long commandBufferHandle,
        int queueFamilyIndex,
        long deviceGeneration,
        long resourceGeneration) {

    public CommandRecordingContext {
        if (commandBufferHandle == 0) {
            throw new IllegalArgumentException("commandBufferHandle must be non-zero");
        }
        if (queueFamilyIndex < 0) {
            throw new IllegalArgumentException("queueFamilyIndex must be non-negative");
        }
        if (deviceGeneration < 1 || resourceGeneration < 1) {
            throw new IllegalArgumentException("generations must be positive");
        }
    }
}
