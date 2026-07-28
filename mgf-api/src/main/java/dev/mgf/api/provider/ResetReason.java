package dev.mgf.api.provider;

/** Reasons that invalidate temporal provider state. */
public enum ResetReason {
    FIRST_FRAME,
    RESIZE,
    RESOURCE_RELOAD,
    WORLD_CHANGE,
    DIMENSION_CHANGE,
    CAMERA_DISCONTINUITY,
    PROVIDER_CHANGE,
    DEVICE_REPLACED
}
