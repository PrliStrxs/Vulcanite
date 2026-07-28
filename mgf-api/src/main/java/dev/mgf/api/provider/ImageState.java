package dev.mgf.api.provider;

/** Native image state at a provider callback boundary. */
public record ImageState(
        int layout,
        long stageMask,
        long accessMask,
        int queueFamilyIndex) {

    public ImageState {
        if (stageMask < 0 || accessMask < 0 || queueFamilyIndex < -1) {
            throw new IllegalArgumentException("invalid native image state");
        }
    }
}
