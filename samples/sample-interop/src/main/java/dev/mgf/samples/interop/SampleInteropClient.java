package dev.mgf.samples.interop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import dev.mgf.api.Mgf;
import dev.mgf.api.MgfRuntime;

/**
 * Logs the negotiated device state once the client has started. Reading this
 * log against the M0 acceptance criteria is the whole point of the sample:
 * requested extensions must appear in the enabled set (on supporting GPUs),
 * and every interop handle must be non-zero.
 */
public final class SampleInteropClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");

    @Override
    public void onInitializeClient() {
        SampleFrameGraphProbe.install();
        SampleVignette.install();
        SampleWorldGeometry.install();
        SampleAutoExposure.install();
        SampleDepthReadback.install();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> report());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SampleWorldGeometry.close());
    }

    private static void report() {
        if (!Mgf.isAvailable()) {
            LOGGER.error("MGF runtime unavailable — is the mgf mod loaded?");
            return;
        }
        MgfRuntime runtime = Mgf.runtime();
        LOGGER.info("MGF {} | backend={} | tier={} | negotiationActive={}",
                runtime.version(),
                runtime.activeBackend(),
                runtime.caps().tier(),
                runtime.caps().extensionNegotiationActive());

        LOGGER.info("Compute: available={} reason={}",
                runtime.caps().hasCompute(),
                runtime.caps().computeUnavailableReason().orElse("available"));

        reportExtension(runtime, SampleVulkanBoot.EXT_FORMAT_FEATURE_FLAGS_2);
        reportExtension(runtime, SampleVulkanBoot.EXT_OPTICAL_FLOW);
        reportExtension(runtime, SampleVulkanBoot.EXT_EXTERNAL_MEMORY);
        reportExtension(runtime, SampleVulkanBoot.EXT_EXTERNAL_MEMORY_WIN32);

        runtime.vkInterop().ifPresentOrElse(
                interop -> LOGGER.info(
                        "VkInterop: instance=0x{} physicalDevice=0x{} device=0x{} graphicsQueue=0x{} (family {}) computeQueue=0x{} (family {}) transferQueue=0x{} (family {}) vma=0x{}",
                        Long.toHexString(interop.vkInstance()),
                        Long.toHexString(interop.vkPhysicalDevice()),
                        Long.toHexString(interop.vkDevice()),
                        Long.toHexString(interop.graphicsQueue()), interop.graphicsQueueFamily(),
                        Long.toHexString(interop.computeQueue()), interop.computeQueueFamily(),
                        Long.toHexString(interop.transferQueue()), interop.transferQueueFamily(),
                        Long.toHexString(interop.vmaAllocator())),
                () -> LOGGER.info("VkInterop not available (expected on the OpenGL backend)"));
    }

    private static void reportExtension(MgfRuntime runtime, String extension) {
        LOGGER.info("Requested extension {} -> enabled={}", extension, runtime.caps().hasDeviceExtension(extension));
    }
}
