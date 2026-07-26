package dev.mgf.samples.interop;

import dev.mgf.api.vk.VulkanBootConfigurator;
import dev.mgf.api.vk.VulkanBootRegistrar;

/**
 * Requests the extensions the M0 spike must prove injectable
 * (design doc §10, milestone M0).
 */
public final class SampleVulkanBoot implements VulkanBootRegistrar {

    static final String EXT_OPTICAL_FLOW = "VK_NV_optical_flow";
    static final String EXT_EXTERNAL_MEMORY = "VK_KHR_external_memory";
    static final String EXT_EXTERNAL_MEMORY_WIN32 = "VK_KHR_external_memory_win32";

    @Override
    public void configureVulkan(VulkanBootConfigurator configurator) {
        configurator.requestDeviceExtension(EXT_OPTICAL_FLOW, false);
        configurator.requestDeviceExtension(EXT_EXTERNAL_MEMORY, false);
        configurator.requestDeviceExtension(EXT_EXTERNAL_MEMORY_WIN32, false);
    }
}
