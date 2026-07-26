package dev.mgf.smoke;

import java.util.Set;

import dev.mgf.api.vk.VulkanBootConfigurator;
import dev.mgf.api.vk.VulkanBootRegistrar;

/**
 * Boot-time half of the smoke test: requests one universally available
 * extension, one deliberately nonexistent required extension, and records the
 * {@code onDeviceCreated} delivery for {@link SmokeChecks} to assert on.
 */
public final class SmokeVulkanBoot implements VulkanBootRegistrar {

    static final String EXT_AVAILABLE = "VK_KHR_external_memory";
    static final String EXT_NONEXISTENT = "VK_MGF_smoke_nonexistent";

    /** Set on the render thread during device creation; read at CLIENT_STARTED. */
    static volatile boolean callbackFired;
    static volatile boolean availableEnabled;
    static volatile Set<String> missingRequired;
    static volatile long callbackVkDevice;

    @Override
    public void configureVulkan(VulkanBootConfigurator configurator) {
        configurator.requestDeviceExtension(EXT_AVAILABLE, false);
        configurator.requestDeviceExtension(EXT_NONEXISTENT, true);

        configurator.onDeviceCreated(result -> {
            callbackFired = true;
            availableEnabled = result.isExtensionEnabled(EXT_AVAILABLE);
            missingRequired = result.missingRequiredExtensions();
            callbackVkDevice = result.interop().vkDevice();
        });
    }
}
