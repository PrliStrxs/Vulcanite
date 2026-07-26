package dev.mgf.impl.core;

import java.util.Set;

import dev.mgf.api.CapsTier;
import dev.mgf.api.GraphicsCaps;
import dev.mgf.impl.vk.VulkanDeviceAccess;

/** {@link GraphicsCaps} computed live from the current device and seam health. */
public final class GraphicsCapsImpl implements GraphicsCaps {

    @Override
    public CapsTier tier() {
        if (VulkanDeviceAccess.current().isEmpty()) {
            return CapsTier.OPENGL_COMPAT;
        }
        return SeamHealth.isEngaged(SeamHealth.Seam.EXTENSION_NEGOTIATION)
                ? CapsTier.VULKAN_FULL
                : CapsTier.VULKAN_BASIC;
    }

    @Override
    public boolean hasDeviceExtension(String extensionName) {
        return enabledDeviceExtensions().contains(extensionName);
    }

    @Override
    public Set<String> enabledDeviceExtensions() {
        return VulkanDeviceAccess.current()
                .map(device -> device.getDeviceInfo().underlyingExtensions())
                .orElse(Set.of());
    }

    @Override
    public boolean extensionNegotiationActive() {
        return SeamHealth.isEngaged(SeamHealth.Seam.EXTENSION_NEGOTIATION);
    }
}
