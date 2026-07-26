package dev.mgf.api.vk;

import java.util.Set;

/**
 * Read access to the live Vulkan objects behind the vanilla device, for native
 * bridges (optical flow, external renderers, external-memory interop).
 *
 * <p>All handles are raw Vulkan handles ({@code long} addresses; dispatchable
 * handles are the underlying {@code Vk*} pointer). The API deliberately avoids
 * LWJGL and vanilla types so that a game update can never break consumer
 * compile-time signatures. Wrap them with LWJGL on the consumer side, e.g.
 * {@code new VkDevice(handle, physicalDevice, createInfo)} equivalents are NOT
 * needed for most native bridges — pass the raw handles across JNI directly.
 *
 * <p>Ownership: all objects belong to vanilla. Consumers must not destroy them,
 * must not leave them in a modified state (image layouts, queue state), and
 * must synchronize external submissions with timeline semaphores.
 */
public interface VkInterop {

    /** @return {@code VkInstance} handle */
    long vkInstance();

    /** @return {@code VkPhysicalDevice} handle */
    long vkPhysicalDevice();

    /** @return {@code VkDevice} handle */
    long vkDevice();

    /** @return {@code VkQueue} handle of the main graphics queue */
    long graphicsQueue();

    /** @return queue family index of {@link #graphicsQueue()} */
    int graphicsQueueFamily();

    /** @return {@code VkQueue} handle of vanilla's dedicated compute queue */
    long computeQueue();

    /** @return queue family index of {@link #computeQueue()} */
    int computeQueueFamily();

    /** @return {@code VkQueue} handle of vanilla's dedicated transfer queue */
    long transferQueue();

    /** @return queue family index of {@link #transferQueue()} */
    int transferQueueFamily();

    /** @return vanilla's {@code VmaAllocator} handle */
    long vmaAllocator();

    /** @return extensions actually enabled on {@link #vkDevice()} */
    Set<String> enabledDeviceExtensions();
}
