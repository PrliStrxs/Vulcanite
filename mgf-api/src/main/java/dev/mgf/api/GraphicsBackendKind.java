package dev.mgf.api;

/** The graphics backend vanilla is actually running on. */
public enum GraphicsBackendKind {
    VULKAN,
    OPENGL,
    /** Device not created yet, or an unrecognized future backend. */
    UNKNOWN
}
