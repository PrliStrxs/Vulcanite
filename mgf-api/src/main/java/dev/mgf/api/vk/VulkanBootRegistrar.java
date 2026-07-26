package dev.mgf.api.vk;

/**
 * Entrypoint interface for participating in Vulkan device creation.
 *
 * <p>Register in {@code fabric.mod.json}:
 * <pre>{@code
 * "entrypoints": {
 *   "mgf:vulkan_boot": [ "com.example.MyVulkanBoot" ]
 * }
 * }</pre>
 *
 * <p>MGF invokes all registrars exactly once, on the render thread, immediately
 * before the Vulkan device is created. If the game runs on OpenGL (or the
 * negotiation seam failed to apply), registrars are never invoked.
 *
 * <p>Implementations must be fast and must not touch the graphics device —
 * it does not exist yet.
 */
public interface VulkanBootRegistrar {

    /** Declare extension requests through the supplied configurator. */
    void configureVulkan(VulkanBootConfigurator configurator);
}
