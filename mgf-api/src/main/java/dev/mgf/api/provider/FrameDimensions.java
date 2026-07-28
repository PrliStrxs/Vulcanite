package dev.mgf.api.provider;

/** Input render and final display dimensions for one session or frame. */
public record FrameDimensions(
        int renderWidth,
        int renderHeight,
        int displayWidth,
        int displayHeight) {

    public FrameDimensions {
        if (renderWidth < 1 || renderHeight < 1 || displayWidth < 1 || displayHeight < 1) {
            throw new IllegalArgumentException("all frame dimensions must be positive");
        }
    }
}
