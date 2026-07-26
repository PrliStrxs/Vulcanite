package dev.mgf.api.vk;

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
     * @param required whether the requesting mod cannot function without it
     *        (affects log severity only — MGF still boots either way)
     */
    void requestDeviceExtension(String extensionName, boolean required);
}
