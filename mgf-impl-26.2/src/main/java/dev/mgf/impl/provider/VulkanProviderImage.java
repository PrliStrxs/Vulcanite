package dev.mgf.impl.provider;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;

import java.nio.LongBuffer;
import java.util.Objects;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;

import dev.mgf.api.provider.BorrowedImage;
import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.ImageLifetime;
import dev.mgf.api.provider.ImageOwnership;
import dev.mgf.api.provider.ImageState;

/** MGF-owned device-local image exposed to providers only through an opaque descriptor. */
final class VulkanProviderImage implements VulkanProviderResources.OwnedImage {

    static final ImageLifetime DESCRIPTOR_LIFETIME = ImageLifetime.CALLBACK;
    static final int FORMAT = VK_FORMAT_R8G8B8A8_UNORM;
    static final int USAGE = VK_IMAGE_USAGE_STORAGE_BIT
            | VK_IMAGE_USAGE_SAMPLED_BIT
            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
            | VK_IMAGE_USAGE_TRANSFER_DST_BIT;

    private final VulkanDevice device;
    private final int width;
    private final int height;
    private final long image;
    private final long allocation;
    private final long view;
    private boolean initialized;
    private boolean destroyed;

    VulkanProviderImage(VulkanDevice device, String label, int width, int height) {
        this.device = Objects.requireNonNull(device, "device");
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        this.width = width;
        this.height = height;

        long createdImage = 0L;
        long createdAllocation = 0L;
        long createdView = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(FORMAT)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(USAGE)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent().set(width, height, 1);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer imageHandle = stack.callocLong(1);
            PointerBuffer allocationHandle = stack.callocPointer(1);
            VulkanUtils.crashIfFailure(device,
                    Vma.vmaCreateImage(device.vma(), imageInfo, allocationInfo,
                            imageHandle, allocationHandle, null),
                    "Failed to create provider image " + label);
            createdImage = imageHandle.get(0);
            createdAllocation = allocationHandle.get(0);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(createdImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(FORMAT);
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            LongBuffer viewHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device,
                    VK10.vkCreateImageView(device.vkDevice(), viewInfo, null, viewHandle),
                    "Failed to create provider image view " + label);
            createdView = viewHandle.get(0);
        } catch (Throwable throwable) {
            if (createdView != 0L) {
                VK10.vkDestroyImageView(device.vkDevice(), createdView, null);
            }
            if (createdImage != 0L) {
                Vma.vmaDestroyImage(device.vma(), createdImage, createdAllocation);
            }
            throw throwable;
        }
        image = createdImage;
        allocation = createdAllocation;
        view = createdView;
        Vma.vmaSetAllocationName(device.vma(), allocation, label);
    }

    BorrowedImage descriptor(
            ColorEncoding colorEncoding,
            ImageState state,
            long deviceGeneration,
            long resourceGeneration) {
        if (destroyed) {
            throw new IllegalStateException("provider image is destroyed");
        }
        return new BorrowedImage(
                image,
                view,
                width,
                height,
                FORMAT,
                Integer.toUnsignedLong(USAGE),
                colorEncoding,
                state,
                ImageOwnership.MGF,
                DESCRIPTOR_LIFETIME,
                deviceGeneration,
                resourceGeneration);
    }

    long imageHandle() {
        return image;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean initialized() {
        return initialized;
    }

    void markInitialized() {
        initialized = true;
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        VK10.vkDestroyImageView(device.vkDevice(), view, null);
        Vma.vmaDestroyImage(device.vma(), image, allocation);
    }
}
