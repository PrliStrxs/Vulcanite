package dev.mgf.impl.vk;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.mgf.api.GraphicsAdapterVendor;

/**
 * Resolves the live {@link VulkanDevice} behind vanilla's {@link GpuDevice}
 * facade. The {@code backend} field is opened by {@code mgf.accesswidener};
 * this class is the only place allowed to touch it.
 */
public final class VulkanDeviceAccess {

    private VulkanDeviceAccess() {
    }

    /** @return the live Vulkan device backend, if the game runs on Vulkan */
    public static Optional<VulkanDevice> current() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return Optional.empty();
        }
        return device.backend instanceof VulkanDevice vulkanDevice
                ? Optional.of(vulkanDevice)
                : Optional.empty();
    }

    /** @return the current facade device, if created */
    public static Optional<GpuDevice> gpuDevice() {
        return Optional.ofNullable(RenderSystem.tryGetDevice());
    }

    /** Suffix vanilla appends to device-extension entries in {@code underlyingExtensions} (26.2). */
    private static final String DEVICE_SUFFIX = " (D)";
    /** Suffix vanilla appends to instance-extension entries in {@code underlyingExtensions} (26.2). */
    private static final String INSTANCE_SUFFIX = " (I)";

    /**
     * Clean names of the extensions enabled on the live Vulkan device.
     *
     * <p>Preferred source is the negotiation snapshot (the exact list passed to
     * {@code vkCreateDevice}). If the negotiation seam did not fire, falls back
     * to parsing vanilla's {@code DeviceInfo.underlyingExtensions}, whose
     * entries are decorated for debug display ({@code "VK_x (D)"} device /
     * {@code "VK_x (I)"} instance — bytecode-verified on 26.2).
     */
    public static Set<String> enabledDeviceExtensions(VulkanDevice device) {
        VulkanBootNegotiation.Outcome outcome = VulkanBootNegotiation.outcome();
        if (outcome != null) {
            return outcome.deviceExtensions();
        }
        Set<String> names = new HashSet<>();
        for (String entry : device.getDeviceInfo().underlyingExtensions()) {
            if (entry.endsWith(DEVICE_SUFFIX)) {
                names.add(entry.substring(0, entry.length() - DEVICE_SUFFIX.length()));
            } else if (!entry.endsWith(INSTANCE_SUFFIX)) {
                names.add(entry); // undecorated entry: pass through, format may change per drop
            }
        }
        return Set.copyOf(names);
    }

    /** @return a conservative vendor classification for the live Vulkan device. */
    public static GraphicsAdapterVendor adapterVendor(VulkanDevice device) {
        VulkanBootNegotiation.Outcome outcome = VulkanBootNegotiation.outcome();
        if (outcome != null) {
            return outcome.adapterVendor();
        }
        return classifyVendor(device.getDeviceInfo().vendorName());
    }

    static GraphicsAdapterVendor classifyVendor(String vendorName) {
        if (vendorName == null) {
            return GraphicsAdapterVendor.UNKNOWN;
        }
        String lower = vendorName.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("nvidia")) {
            return GraphicsAdapterVendor.NVIDIA;
        }
        if (lower.contains("amd") || lower.contains("advanced micro devices")
                || lower.contains("ati")) {
            return GraphicsAdapterVendor.AMD;
        }
        if (lower.contains("intel")) {
            return GraphicsAdapterVendor.INTEL;
        }
        if (lower.contains("swiftshader") || lower.contains("software")
                || lower.contains("llvmpipe")) {
            return GraphicsAdapterVendor.SOFTWARE;
        }
        return GraphicsAdapterVendor.UNKNOWN;
    }
}
