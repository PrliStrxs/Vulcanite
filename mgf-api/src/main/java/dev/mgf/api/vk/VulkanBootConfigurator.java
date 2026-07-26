package dev.mgf.api.vk;

import java.util.function.Consumer;

/**
 * Collects Vulkan device-creation requests from a {@link VulkanBootRegistrar}.
 *
 * <p>Semantics: MGF never fails device creation on behalf of a consumer.
 * A requested extension is enabled only if the physical device supports it;
 * query the outcome later via {@link dev.mgf.api.GraphicsCaps#hasDeviceExtension}.
 * "Required" only affects logging: a missing required extension is logged as a
 * warning so users can diagnose why the requesting mod disabled itself.
 *
 * <p>Instance-level extensions and feature-struct (pNext) requests are not yet
 * part of the stable API; they are planned once the seam hardens (see design
 * doc §6.3).
 */
public interface VulkanBootConfigurator {

    /**
     * Request a Vulkan device extension.
     *
     * @param extensionName e.g. {@code "VK_NV_optical_flow"}
     * @param required whether the requesting mod cannot function without it.
     *        MGF still boots either way; a missing required extension is logged
     *        as a warning and reported via
     *        {@link VulkanBootResult#missingRequiredExtensions()}
     */
    void requestDeviceExtension(String extensionName, boolean required);

    /**
     * Register a callback fired once, on the render thread, right after the
     * Vulkan device has been created — the earliest point where interop
     * handles are valid.
     *
     * <p>Never fires when the game runs on OpenGL (or if the device-created
     * seam failed to apply on this game version); use a client lifecycle event
     * plus {@link dev.mgf.api.GraphicsCaps} as the fallback path for that case.
     *
     * <p>Callbacks must not throw — exceptions are caught, logged, and
     * swallowed so device creation can never be broken by a consumer.
     */
    void onDeviceCreated(Consumer<VulkanBootResult> callback);
}
