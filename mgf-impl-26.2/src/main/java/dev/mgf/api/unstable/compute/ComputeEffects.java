package dev.mgf.api.unstable.compute;

import dev.mgf.impl.compute.ComputeAutoExposureRegistry;

/** High-level compute effects that preserve MGF's resource-state contract. */
public final class ComputeEffects {

    private ComputeEffects() {
    }

    /**
     * Registers a luminance-histogram auto-exposure pass for the level's main
     * color target. Vulkan executes the effect; OpenGL keeps the original
     * image and exposes the disabled reason through {@link ComputeServices}.
     */
    public static void registerMainColorAutoExposure(String name) {
        ComputeAutoExposureRegistry.register(name);
    }
}
