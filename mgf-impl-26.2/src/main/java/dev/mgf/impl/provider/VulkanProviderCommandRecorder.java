package dev.mgf.impl.provider;

import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

import java.util.Objects;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;

import dev.mgf.api.provider.CommandRecordingContext;
import dev.mgf.api.provider.ImageState;

/** One transient graphics-queue command buffer for MGF-managed provider work. */
final class VulkanProviderCommandRecorder implements AutoCloseable {

    private static final long MEMORY_READ_WRITE = VK_ACCESS_2_MEMORY_READ_BIT_KHR
            | VK_ACCESS_2_MEMORY_WRITE_BIT_KHR;

    private final VulkanDevice device;
    private final VulkanCommandEncoder encoder;
    private final VkCommandBuffer commandBuffer;
    private final long deviceGeneration;
    private final long resourceGeneration;
    private boolean ended;

    VulkanProviderCommandRecorder(
            VulkanDevice device, long deviceGeneration, long resourceGeneration) {
        this.device = Objects.requireNonNull(device, "device");
        if (deviceGeneration < 1 || resourceGeneration < 1) {
            throw new IllegalArgumentException("generations must be positive");
        }
        this.deviceGeneration = deviceGeneration;
        this.resourceGeneration = resourceGeneration;
        this.encoder = device.createCommandEncoder();
        this.commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
    }

    CommandRecordingContext context() {
        ensureRecording();
        return new CommandRecordingContext(
                commandBuffer.address(),
                device.graphicsQueue().queueFamilyIndex(),
                deviceGeneration,
                resourceGeneration);
    }

    ImageState providerReadState() {
        return state(VK_ACCESS_2_MEMORY_READ_BIT_KHR);
    }

    ImageState providerReadWriteState() {
        return state(MEMORY_READ_WRITE);
    }

    void prepareProviderRead(long image) {
        barrier(image, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_READ_BIT_KHR);
    }

    void prepareProviderWrite(VulkanProviderImage image) {
        Objects.requireNonNull(image, "image");
        int oldLayout = image.initialized() ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_UNDEFINED;
        long sourceStage = image.initialized() ? VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR : 0L;
        long sourceAccess = image.initialized() ? MEMORY_READ_WRITE : 0L;
        barrier(image.imageHandle(), oldLayout, VK_IMAGE_LAYOUT_GENERAL,
                sourceStage, sourceAccess,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, MEMORY_READ_WRITE);
        image.markInitialized();
    }

    void finishProviderWrite(VulkanProviderImage image) {
        Objects.requireNonNull(image, "image");
        barrier(image.imageHandle(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_READ_BIT_KHR);
    }

    void copyMinecraftToOwned(long minecraftImage, VulkanProviderImage destination, int width, int height) {
        validateCopy(minecraftImage, destination, width, height);
        barrier(minecraftImage, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_READ_BIT_KHR);
        prepareTransferDestination(destination);
        copy(minecraftImage, destination.imageHandle(), width, height);
    }

    void copyOwnedToMinecraft(VulkanProviderImage source, long minecraftImage, int width, int height) {
        validateCopy(minecraftImage, source, width, height);
        prepareTransferSource(source);
        barrier(minecraftImage, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, MEMORY_READ_WRITE,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
        copy(source.imageHandle(), minecraftImage, width, height);
        restoreMinecraftForBlit(minecraftImage);
    }

    void copyOwnedToOwned(
            VulkanProviderImage source, VulkanProviderImage destination, int width, int height) {
        Objects.requireNonNull(source, "source");
        validateCopy(source.imageHandle(), destination, width, height);
        prepareTransferSource(source);
        prepareTransferDestination(destination);
        copy(source.imageHandle(), destination.imageHandle(), width, height);
    }

    void restoreMinecraftForBlit(long minecraftImage) {
        requireHandle(minecraftImage, "minecraftImage");
        barrier(minecraftImage, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_READ_BIT_KHR);
    }

    void prepareMinecraftForBlit(long minecraftImage) {
        requireHandle(minecraftImage, "minecraftImage");
        barrier(minecraftImage, VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, MEMORY_READ_WRITE,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_READ_BIT_KHR);
    }

    void finish() {
        ensureRecording();
        VulkanUtils.crashIfFailure(device, VK10.vkEndCommandBuffer(commandBuffer),
                "Failed to end provider command buffer");
        ended = true;
        encoder.execute(commandBuffer);
    }

    @Override
    public void close() {
        if (ended) {
            return;
        }
        ended = true;
        VulkanUtils.crashIfFailure(device, VK10.vkEndCommandBuffer(commandBuffer),
                "Failed to abort provider command buffer");
    }

    private void prepareTransferSource(VulkanProviderImage image) {
        if (!image.initialized()) {
            throw new IllegalStateException("provider source image is uninitialized");
        }
        barrier(image.imageHandle(), VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR, VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_READ_BIT_KHR);
    }

    private void prepareTransferDestination(VulkanProviderImage image) {
        int oldLayout = image.initialized() ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_UNDEFINED;
        long sourceStage = image.initialized() ? VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR : 0L;
        long sourceAccess = image.initialized() ? MEMORY_READ_WRITE : 0L;
        barrier(image.imageHandle(), oldLayout, VK_IMAGE_LAYOUT_GENERAL,
                sourceStage, sourceAccess,
                VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
        image.markInitialized();
    }

    private void barrier(
            long image,
            int oldLayout,
            int newLayout,
            long sourceStage,
            long sourceAccess,
            long destinationStage,
            long destinationAccess) {
        ensureRecording();
        requireHandle(image, "image");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(sourceStage)
                    .srcAccessMask(sourceAccess)
                    .dstStageMask(destinationStage)
                    .dstAccessMask(destinationAccess)
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(-1)
                    .dstQueueFamilyIndex(-1)
                    .image(image);
            VkImageSubresourceRange range = barriers.subresourceRange();
            range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pImageMemoryBarriers(barriers);
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }
    }

    private void copy(long source, long destination, int width, int height) {
        ensureRecording();
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
            region.extent().set(width, height, 1);
            VK10.vkCmdCopyImage(commandBuffer,
                    source, VK_IMAGE_LAYOUT_GENERAL,
                    destination, VK_IMAGE_LAYOUT_GENERAL,
                    region);
        }
    }

    private ImageState state(long accessMask) {
        return new ImageState(
                VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                accessMask,
                device.graphicsQueue().queueFamilyIndex());
    }

    private static void validateCopy(
            long externalImage, VulkanProviderImage ownedImage, int width, int height) {
        requireHandle(externalImage, "externalImage");
        Objects.requireNonNull(ownedImage, "ownedImage");
        if (width < 1 || height < 1
                || width > ownedImage.width() || height > ownedImage.height()) {
            throw new IllegalArgumentException("copy extent exceeds the owned image");
        }
    }

    private static void requireHandle(long handle, String name) {
        if (handle == 0L) {
            throw new IllegalArgumentException(name + " must be non-zero");
        }
    }

    private void ensureRecording() {
        if (ended) {
            throw new IllegalStateException("provider command buffer is no longer recording");
        }
    }
}
