package dev.mgf.api;

/**
 * Coarse capability tier. Consumer mods gate premium features on this and
 * degrade cleanly on lower tiers instead of crashing.
 */
public enum CapsTier {
    /** Vulkan backend with all MGF seams active (extension negotiation, interop). */
    VULKAN_FULL,
    /** Vulkan backend, but one or more MGF seams failed to apply on this game version. */
    VULKAN_BASIC,
    /** OpenGL backend: MGF loads but all Vulkan-only services are disabled. */
    OPENGL_COMPAT
}
