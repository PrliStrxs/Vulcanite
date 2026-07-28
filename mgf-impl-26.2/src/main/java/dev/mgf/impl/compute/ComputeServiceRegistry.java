package dev.mgf.impl.compute;

import java.util.Optional;

import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.mgf.api.unstable.compute.ComputeDispatcher;
import dev.mgf.impl.vk.VulkanDeviceAccess;

/** Owns the one compute dispatcher associated with the live graphics device. */
public final class ComputeServiceRegistry {

    private static VulkanDevice device;
    private static VulkanComputeDispatcher dispatcher;
    private static String initializationFailure;

    private ComputeServiceRegistry() {
    }

    public static synchronized Optional<ComputeDispatcher> current() {
        Optional<VulkanDevice> current = VulkanDeviceAccess.current();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        VulkanDevice liveDevice = current.get();
        if (dispatcher != null && device == liveDevice) {
            return Optional.of(dispatcher);
        }
        if (initializationFailure != null && device == liveDevice) {
            return Optional.empty();
        }
        closeCurrent();
        device = liveDevice;
        try {
            dispatcher = new VulkanComputeDispatcher(liveDevice);
            return Optional.of(dispatcher);
        } catch (Throwable t) {
            initializationFailure = describe(t);
            return Optional.empty();
        }
    }

    public static synchronized Optional<String> unavailableReason() {
        if (current().isPresent()) {
            return Optional.empty();
        }
        if (initializationFailure != null) {
            return Optional.of("Vulkan compute initialization failed: " + initializationFailure);
        }
        return VulkanDeviceAccess.gpuDevice().isPresent()
                ? Optional.of("Compute is unavailable on the OpenGL backend")
                : Optional.of("Graphics device is not initialized");
    }

    /** Called at the head of {@code VulkanDevice.close()}, before VMA is destroyed. */
    public static synchronized void onDeviceClosing(VulkanDevice closingDevice) {
        if (device == closingDevice) {
            closeCurrent();
        }
    }

    static synchronized Optional<VulkanComputeDispatcher> vulkanDispatcher() {
        current();
        return Optional.ofNullable(dispatcher);
    }

    private static void closeCurrent() {
        VulkanComputeDispatcher closingDispatcher = dispatcher;
        dispatcher = null;
        device = null;
        initializationFailure = null;
        if (closingDispatcher != null) {
            closingDispatcher.closeNow();
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
