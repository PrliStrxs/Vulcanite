package dev.mgf.impl.vk;

import java.util.Optional;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanDevice;

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
}
