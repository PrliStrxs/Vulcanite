package dev.mgf.api.vk;

import java.util.Set;

/**
 * Delivered to {@link VulkanBootConfigurator#onDeviceCreated} callbacks once the
 * Vulkan device exists. Scoped to the registering mod: extension outcomes refer
 * to that mod's own requests.
 */
public interface VulkanBootResult {

    /** @return live interop access to the freshly created device */
    VkInterop interop();

    /**
     * @param extensionName a device extension name
     * @return whether it is enabled on the live device
     */
    boolean isExtensionEnabled(String extensionName);

    /** @return all extensions enabled on the live device */
    Set<String> enabledDeviceExtensions();

    /**
     * Extensions this mod requested with {@code required = true} that could not
     * be enabled. Non-empty means the mod should disable the dependent feature
     * (and may tell the user why). MGF has already logged a warning for each.
     */
    Set<String> missingRequiredExtensions();
}
