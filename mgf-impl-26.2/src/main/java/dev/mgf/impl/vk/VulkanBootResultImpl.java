package dev.mgf.impl.vk;

import java.util.Set;

import dev.mgf.api.vk.VkInterop;
import dev.mgf.api.vk.VulkanBootResult;

/** Per-mod {@link VulkanBootResult}: shared device state + that mod's own outcomes. */
public final class VulkanBootResultImpl implements VulkanBootResult {

    private final VkInterop interop;
    private final Set<String> enabledDeviceExtensions;
    private final Set<String> missingRequiredExtensions;

    public VulkanBootResultImpl(VkInterop interop,
                                Set<String> enabledDeviceExtensions,
                                Set<String> missingRequiredExtensions) {
        this.interop = interop;
        this.enabledDeviceExtensions = enabledDeviceExtensions;
        this.missingRequiredExtensions = missingRequiredExtensions;
    }

    @Override
    public VkInterop interop() {
        return interop;
    }

    @Override
    public boolean isExtensionEnabled(String extensionName) {
        return enabledDeviceExtensions.contains(extensionName);
    }

    @Override
    public Set<String> enabledDeviceExtensions() {
        return enabledDeviceExtensions;
    }

    @Override
    public Set<String> missingRequiredExtensions() {
        return missingRequiredExtensions;
    }
}
