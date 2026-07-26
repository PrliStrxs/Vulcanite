package dev.mgf.impl.vk;

import java.util.Set;

import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.mgf.api.vk.VkInterop;

/**
 * {@link VkInterop} backed by the live vanilla {@link VulkanDevice}.
 * Handles are extracted on every call — they are stable for the lifetime of
 * the device, and going through the accessors keeps this class stateless.
 */
public final class VkInteropImpl implements VkInterop {

    private final VulkanDevice device;

    public VkInteropImpl(VulkanDevice device) {
        this.device = device;
    }

    @Override
    public long vkInstance() {
        return device.instance().vkInstance().address();
    }

    @Override
    public long vkPhysicalDevice() {
        VulkanBootNegotiation.Outcome outcome = VulkanBootNegotiation.outcome();
        return outcome != null ? outcome.vkPhysicalDevice() : 0L;
    }

    @Override
    public long vkDevice() {
        return device.vkDevice().address();
    }

    @Override
    public long graphicsQueue() {
        return device.graphicsQueue().vkQueue().address();
    }

    @Override
    public int graphicsQueueFamily() {
        return device.graphicsQueue().queueFamilyIndex();
    }

    @Override
    public long computeQueue() {
        return device.computeQueue().vkQueue().address();
    }

    @Override
    public int computeQueueFamily() {
        return device.computeQueue().queueFamilyIndex();
    }

    @Override
    public long transferQueue() {
        return device.transferQueue().vkQueue().address();
    }

    @Override
    public int transferQueueFamily() {
        return device.transferQueue().queueFamilyIndex();
    }

    @Override
    public long vmaAllocator() {
        return device.vma();
    }

    @Override
    public Set<String> enabledDeviceExtensions() {
        return device.getDeviceInfo().underlyingExtensions();
    }
}
