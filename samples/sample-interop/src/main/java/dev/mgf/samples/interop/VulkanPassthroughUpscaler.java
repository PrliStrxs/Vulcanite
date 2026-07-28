package dev.mgf.samples.interop;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;

import dev.mgf.api.provider.BorrowedImage;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.vk.VkInterop;

/** Development-only native-size copy used to exercise the successful output path. */
final class VulkanPassthroughUpscaler {

    private final VkDevice device;

    VulkanPassthroughUpscaler(ProviderSessionContext context) {
        VkInterop interop = context.environment().vulkanInterop().orElseThrow(
                () -> new IllegalArgumentException("Vulkan passthrough requires Vulkan interop"));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo applicationInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .apiVersion(VK_API_VERSION_1_2);
            VkInstanceCreateInfo instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(applicationInfo);
            VkInstance instance = new VkInstance(interop.vkInstance(), instanceCreateInfo);
            VkPhysicalDevice physicalDevice = new VkPhysicalDevice(interop.vkPhysicalDevice(), instance);
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack).sType$Default();
            device = new VkDevice(interop.vkDevice(), physicalDevice, deviceCreateInfo, VK_API_VERSION_1_2);
        }
    }

    void record(UpscaleFrame frame) {
        BorrowedImage input = frame.resources().inputColor();
        BorrowedImage output = frame.resources().outputColor();
        validate(input, output, frame.dimensions().displayWidth(), frame.dimensions().displayHeight());

        VkCommandBuffer commandBuffer = new VkCommandBuffer(
                frame.command().commandBufferHandle(), device);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
            region.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            region.extent().set(frame.dimensions().displayWidth(), frame.dimensions().displayHeight(), 1);
            VK10.vkCmdCopyImage(
                    commandBuffer,
                    input.imageHandle(), VK_IMAGE_LAYOUT_GENERAL,
                    output.imageHandle(), VK_IMAGE_LAYOUT_GENERAL,
                    region);
        }
    }

    private static void validate(BorrowedImage input, BorrowedImage output, int width, int height) {
        if (input.nativeFormat() != output.nativeFormat()) {
            throw new IllegalArgumentException("passthrough images must use the same native format");
        }
        if (input.width() < width || input.height() < height
                || output.width() < width || output.height() < height) {
            throw new IllegalArgumentException("passthrough extent exceeds an image");
        }
        if ((input.nativeUsageMask() & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) == 0
                || (output.nativeUsageMask() & VK_IMAGE_USAGE_TRANSFER_DST_BIT) == 0) {
            throw new IllegalArgumentException("passthrough images require transfer usage");
        }
    }
}
