package dev.mgf.samples.interop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.mgf.api.vk.VulkanBootConfigurator;
import dev.mgf.api.vk.VulkanBootRegistrar;

/**
 * Requests the extensions the M0 spike must prove injectable (design doc §10,
 * milestone M0), plus a deliberately nonexistent required extension to
 * exercise the missing-required reporting path, and demonstrates the
 * {@code onDeviceCreated} callback.
 */
public final class SampleVulkanBoot implements VulkanBootRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");

    static final String EXT_FORMAT_FEATURE_FLAGS_2 = "VK_KHR_format_feature_flags2";
    static final String EXT_OPTICAL_FLOW = "VK_NV_optical_flow";
    static final String EXT_EXTERNAL_MEMORY = "VK_KHR_external_memory";
    static final String EXT_EXTERNAL_MEMORY_WIN32 = "VK_KHR_external_memory_win32";
    /** Never exists on any driver: verifies the missing-required path end to end. */
    static final String EXT_NONEXISTENT = "VK_MGF_nonexistent_test";

    @Override
    public void configureVulkan(VulkanBootConfigurator configurator) {
        configurator.requestDeviceExtension(EXT_FORMAT_FEATURE_FLAGS_2, false);
        configurator.requestDeviceExtension(EXT_OPTICAL_FLOW, false);
        configurator.requestDeviceExtension(EXT_EXTERNAL_MEMORY, false);
        configurator.requestDeviceExtension(EXT_EXTERNAL_MEMORY_WIN32, false);
        configurator.requestDeviceExtension(EXT_NONEXISTENT, true);

        configurator.onDeviceCreated(result -> LOGGER.info(
                "onDeviceCreated: opticalFlow={} externalMemory={} missingRequired={} device=0x{}",
                result.isExtensionEnabled(EXT_OPTICAL_FLOW),
                result.isExtensionEnabled(EXT_EXTERNAL_MEMORY),
                result.missingRequiredExtensions(),
                Long.toHexString(result.interop().vkDevice())));
    }
}
