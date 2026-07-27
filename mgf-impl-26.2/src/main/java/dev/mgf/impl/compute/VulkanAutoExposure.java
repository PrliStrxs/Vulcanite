package dev.mgf.impl.compute;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE;
import static org.lwjgl.vulkan.KHRPushDescriptor.vkCmdPushDescriptorSetKHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_TRANSFER_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;

import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier2;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import net.minecraft.client.Minecraft;

import dev.mgf.api.unstable.compute.ComputeDispatcher;
import dev.mgf.impl.core.MgfConstants;

/** Three-stage histogram, reduction, and exposure-application compute chain. */
final class VulkanAutoExposure {

    private static final int HISTOGRAM_BINS = 256;

    private static final String HISTOGRAM_SHADER = """
            #version 450
            layout(local_size_x = 16, local_size_y = 16) in;
            layout(binding = 0) uniform sampler2D SceneColor;
            layout(std430, binding = 1) buffer Histogram { uint bins[256]; };

            void main() {
                ivec2 size = textureSize(SceneColor, 0);
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (any(greaterThanEqual(pixel, size))) {
                    return;
                }
                vec3 color = texelFetch(SceneColor, pixel, 0).rgb;
                float luminance = max(dot(color, vec3(0.2126, 0.7152, 0.0722)), 0.0009765625);
                float normalized = clamp((log2(luminance) + 10.0) / 12.0, 0.0, 1.0);
                uint bin = uint(normalized * 255.0 + 0.5);
                atomicAdd(bins[bin], 1u);
            }
            """;

    private static final String REDUCE_SHADER = """
            #version 450
            layout(local_size_x = 64) in;
            layout(std430, binding = 0) readonly buffer Histogram { uint bins[256]; };
            layout(std430, binding = 1) buffer ExposureState {
                float exposure;
                float averageLuminance;
                uint pixelCount;
                uint frameCount;
            } state;
            shared float weighted[64];
            shared float counts[64];

            void main() {
                uint index = gl_LocalInvocationID.x;
                float count = 0.0;
                float weight = 0.0;
                for (uint bin = index; bin < 256u; bin += 64u) {
                    float binCount = float(bins[bin]);
                    count += binCount;
                    weight += binCount * float(bin);
                }
                counts[index] = count;
                weighted[index] = weight;
                barrier();
                for (uint stride = 32u; stride > 0u; stride >>= 1u) {
                    if (index < stride) {
                        counts[index] += counts[index + stride];
                        weighted[index] += weighted[index + stride];
                    }
                    barrier();
                }
                if (index == 0u) {
                    float total = max(counts[0], 1.0);
                    float averageBin = weighted[0] / total;
                    float averageLog = mix(-10.0, 2.0, averageBin / 255.0);
                    float luminance = exp2(averageLog);
                    float target = clamp(0.32 / max(luminance, 0.001), 0.55, 1.80);
                    state.exposure = state.frameCount == 0u
                            ? target
                            : mix(state.exposure, target, 0.08);
                    state.averageLuminance = luminance;
                    state.pixelCount = uint(total);
                    state.frameCount += 1u;
                }
            }
            """;

    private static final String APPLY_SHADER = """
            #version 450
            layout(local_size_x = 16, local_size_y = 16) in;
            layout(binding = 0) uniform sampler2D SceneColor;
            layout(std430, binding = 1) readonly buffer ExposureState {
                float exposure;
                float averageLuminance;
                uint pixelCount;
                uint frameCount;
            } state;
            layout(rgba8, binding = 2) uniform writeonly image2D OutputColor;

            void main() {
                ivec2 size = textureSize(SceneColor, 0);
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (any(greaterThanEqual(pixel, size))) {
                    return;
                }
                vec4 source = texelFetch(SceneColor, pixel, 0);
                vec3 adjusted = clamp(source.rgb * state.exposure, 0.0, 1.0);
                imageStore(OutputColor, pixel, vec4(adjusted, source.a));
            }
            """;

    private final VulkanComputeDispatcher dispatcher;
    private final VulkanDevice device;
    private final VulkanComputeProgram histogramProgram;
    private final VulkanComputeProgram reduceProgram;
    private final VulkanComputeProgram applyProgram;
    private final VulkanComputeBuffer histogramBuffer;
    private final VulkanComputeBuffer exposureBuffer;
    private StorageImage output;
    private boolean firstFrame = true;
    private boolean hasRecordedFrame;
    private Object world;
    private boolean closed;

    VulkanAutoExposure(VulkanComputeDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.device = dispatcher.device();
        VulkanComputeProgram histogram = null;
        VulkanComputeProgram reduce = null;
        VulkanComputeProgram apply = null;
        VulkanComputeBuffer histogramData = null;
        VulkanComputeBuffer exposureData = null;
        try {
            histogram = new VulkanComputeProgram(dispatcher, "mgf_auto_exposure_histogram",
                    HISTOGRAM_SHADER,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            reduce = new VulkanComputeProgram(dispatcher, "mgf_auto_exposure_reduce",
                    REDUCE_SHADER,
                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            apply = new VulkanComputeProgram(dispatcher, "mgf_auto_exposure_apply",
                    APPLY_SHADER,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE);
            histogramData = new VulkanComputeBuffer(dispatcher,
                    new ComputeDispatcher.BufferDescriptor(
                            "MGF auto exposure histogram", HISTOGRAM_BINS * Integer.BYTES));
            exposureData = new VulkanComputeBuffer(dispatcher,
                    new ComputeDispatcher.BufferDescriptor("MGF auto exposure state", 16));
        } catch (Throwable t) {
            if (exposureData != null) exposureData.destroy();
            if (histogramData != null) histogramData.destroy();
            if (apply != null) apply.destroy();
            if (reduce != null) reduce.destroy();
            if (histogram != null) histogram.destroy();
            throw t;
        }
        histogramProgram = histogram;
        reduceProgram = reduce;
        applyProgram = apply;
        histogramBuffer = histogramData;
        exposureBuffer = exposureData;
    }

    static boolean supports(RenderTarget target) {
        return target.getColorTexture() != null
                && target.getColorTextureView() instanceof VulkanGpuTextureView
                && target.getColorTexture().getFormat() == GpuFormat.RGBA8_UNORM;
    }

    void execute(RenderTarget target) {
        if (closed) {
            return;
        }
        if (!supports(target)) {
            throw new IllegalStateException("Auto exposure requires a Vulkan RGBA8_UNORM main target");
        }
        VulkanGpuTextureView inputView = (VulkanGpuTextureView) target.getColorTextureView();
        VulkanGpuTexture inputTexture = inputView.texture();
        Object currentWorld = Minecraft.getInstance().level;
        if (currentWorld != world) {
            world = currentWorld;
            firstFrame = true;
        }
        ensureOutput(target.width, target.height);
        VulkanGpuSampler sampler = (VulkanGpuSampler) RenderSystem.getSamplerCache()
                .getClampToEdge(FilterMode.LINEAR);
        VulkanCommandEncoder encoder = device.createCommandEncoder();
        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean outputNeedsInitialization = !output.initialized;
            if (outputNeedsInitialization) {
                imageBarrier(commandBuffer, stack, output.image,
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
                        0L, 0L,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
                output.initialized = true;
            }

            imageBarrier(commandBuffer, stack, inputTexture.vkImage(),
                    VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                    VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR);

            if (hasRecordedFrame) {
                bufferBarrier(commandBuffer, stack, histogramBuffer,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR,
                        VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                        VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
            }
            VK10.vkCmdFillBuffer(commandBuffer, histogramBuffer.vkBuffer(),
                    0, histogramBuffer.size(), 0);
            if (firstFrame) {
                if (hasRecordedFrame) {
                    bufferBarrier(commandBuffer, stack, exposureBuffer,
                            VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                            VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR,
                            VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                            VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
                }
                VK10.vkCmdFillBuffer(commandBuffer, exposureBuffer.vkBuffer(),
                        0, exposureBuffer.size(), 0);
            }
            bufferBarrier(commandBuffer, stack, histogramBuffer,
                    VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            if (firstFrame) {
                bufferBarrier(commandBuffer, stack, exposureBuffer,
                        VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                        VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            }

            VK10.vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    histogramProgram.pipeline());
            pushHistogram(commandBuffer, stack, sampler.vkSampler(),
                    inputView.vkImageView());
            VK10.vkCmdDispatch(commandBuffer, groups(target.width), groups(target.height), 1);

            bufferBarrier(commandBuffer, stack, histogramBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR);
            if (!firstFrame) {
                bufferBarrier(commandBuffer, stack, exposureBuffer,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            }
            VK10.vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    reduceProgram.pipeline());
            pushReduce(commandBuffer, stack);
            VK10.vkCmdDispatch(commandBuffer, 1, 1, 1);

            bufferBarrier(commandBuffer, stack, exposureBuffer,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR);
            if (!outputNeedsInitialization) {
                imageBarrier(commandBuffer, stack, output.image,
                        VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                        VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                        VK_ACCESS_2_TRANSFER_READ_BIT_KHR,
                        VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                        VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            }
            VK10.vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    applyProgram.pipeline());
            pushApply(commandBuffer, stack, sampler.vkSampler(), inputView.vkImageView());
            VK10.vkCmdDispatch(commandBuffer, groups(target.width), groups(target.height), 1);

            imageBarrier(commandBuffer, stack, output.image,
                    VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                    VK_ACCESS_2_TRANSFER_READ_BIT_KHR);
            imageBarrier(commandBuffer, stack, inputTexture.vkImage(),
                    VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR,
                    VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR);
            copyOutputToMain(commandBuffer, stack, inputTexture.vkImage(), target.width, target.height);
            imageBarrier(commandBuffer, stack, inputTexture.vkImage(),
                    VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL,
                    VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                    VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT_KHR
                            | VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT_KHR
                            | VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT_KHR
                            | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT_KHR
                            | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR
                            | VK_ACCESS_2_TRANSFER_READ_BIT_KHR);

            VulkanUtils.crashIfFailure(device, VK10.vkEndCommandBuffer(commandBuffer),
                    "Failed to end auto-exposure command buffer");
        }
        encoder.execute(commandBuffer);
        firstFrame = false;
        hasRecordedFrame = true;
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeStep("submit pending auto-exposure work", () -> device.createCommandEncoder().submit());
        closeStep("wait for auto-exposure work", () -> device.graphicsQueue().waitIdle());
        StorageImage closingOutput = output;
        output = null;
        if (closingOutput != null) {
            closeStep("destroy auto-exposure output", closingOutput::destroy);
        }
        closeStep("destroy auto-exposure state buffer", exposureBuffer::destroy);
        closeStep("destroy auto-exposure histogram buffer", histogramBuffer::destroy);
        closeStep("destroy auto-exposure apply program", applyProgram::destroy);
        closeStep("destroy auto-exposure reduction program", reduceProgram::destroy);
        closeStep("destroy auto-exposure histogram program", histogramProgram::destroy);
    }

    private void ensureOutput(int width, int height) {
        if (output != null && output.width == width && output.height == height) {
            return;
        }
        StorageImage previous = output;
        output = new StorageImage(device, width, height);
        MgfConstants.LOGGER.info("Compute auto exposure output resized to {}x{}", width, height);
        if (previous != null) {
            device.createCommandEncoder().queueForDestroy(previous::destroy);
        }
    }

    private void pushHistogram(VkCommandBuffer commandBuffer, MemoryStack stack,
                               long sampler, long inputView) {
        VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(sampler)
                .imageView(inputView)
                .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorBufferInfo.Buffer histogram = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(histogramBuffer.vkBuffer()).offset(0).range(histogramBuffer.size());
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
        writes.get(0).sType$Default().dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1).pImageInfo(image);
        writes.get(1).sType$Default().dstBinding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1).pBufferInfo(histogram);
        vkCmdPushDescriptorSetKHR(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                histogramProgram.pipelineLayout(), 0, writes);
    }

    private void pushReduce(VkCommandBuffer commandBuffer, MemoryStack stack) {
        VkDescriptorBufferInfo.Buffer buffers = VkDescriptorBufferInfo.calloc(2, stack);
        buffers.get(0).set(histogramBuffer.vkBuffer(), 0, histogramBuffer.size());
        buffers.get(1).set(exposureBuffer.vkBuffer(), 0, exposureBuffer.size());
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
        for (int i = 0; i < 2; i++) {
            writes.get(i).sType$Default().dstBinding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.create(buffers.get(i).address(), 1));
        }
        vkCmdPushDescriptorSetKHR(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                reduceProgram.pipelineLayout(), 0, writes);
    }

    private void pushApply(VkCommandBuffer commandBuffer, MemoryStack stack,
                           long sampler, long inputView) {
        VkDescriptorImageInfo.Buffer input = VkDescriptorImageInfo.calloc(1, stack)
                .sampler(sampler).imageView(inputView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorBufferInfo.Buffer exposure = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(exposureBuffer.vkBuffer()).offset(0).range(exposureBuffer.size());
        VkDescriptorImageInfo.Buffer outputInfo = VkDescriptorImageInfo.calloc(1, stack)
                .imageView(output.view).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
        writes.get(0).sType$Default().dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1).pImageInfo(input);
        writes.get(1).sType$Default().dstBinding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                .descriptorCount(1).pBufferInfo(exposure);
        writes.get(2).sType$Default().dstBinding(2)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .descriptorCount(1).pImageInfo(outputInfo);
        vkCmdPushDescriptorSetKHR(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                applyProgram.pipelineLayout(), 0, writes);
    }

    private void copyOutputToMain(VkCommandBuffer commandBuffer, MemoryStack stack,
                                  long mainImage, int width, int height) {
        VkImageCopy.Buffer region = VkImageCopy.calloc(1, stack);
        region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1);
        region.extent().set(width, height, 1);
        VK10.vkCmdCopyImage(commandBuffer,
                output.image, VK_IMAGE_LAYOUT_GENERAL,
                mainImage, VK_IMAGE_LAYOUT_GENERAL,
                region);
    }

    private static int groups(int size) {
        return (size + 15) / 16;
    }

    private static void closeStep(String action, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            MgfConstants.LOGGER.error("Failed to {}; continuing compute shutdown", action, t);
        }
    }

    private static void bufferBarrier(VkCommandBuffer commandBuffer, MemoryStack stack,
                                      VulkanComputeBuffer buffer,
                                      long sourceStage, long sourceAccess,
                                      long destinationStage, long destinationAccess) {
        VkBufferMemoryBarrier2.Buffer barrier = VkBufferMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .srcStageMask(sourceStage).srcAccessMask(sourceAccess)
                .dstStageMask(destinationStage).dstAccessMask(destinationAccess)
                .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1)
                .buffer(buffer.vkBuffer()).offset(0).size(buffer.size());
        VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                .sType$Default().pBufferMemoryBarriers(barrier);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
    }

    private static void imageBarrier(VkCommandBuffer commandBuffer, MemoryStack stack,
                                     long image, int oldLayout, int newLayout,
                                     long sourceStage, long sourceAccess,
                                     long destinationStage, long destinationAccess) {
        VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .srcStageMask(sourceStage).srcAccessMask(sourceAccess)
                .dstStageMask(destinationStage).dstAccessMask(destinationAccess)
                .oldLayout(oldLayout).newLayout(newLayout)
                .srcQueueFamilyIndex(-1).dstQueueFamilyIndex(-1)
                .image(image);
        VkImageSubresourceRange range = barrier.subresourceRange();
        range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0).levelCount(1)
                .baseArrayLayer(0).layerCount(1);
        VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                .sType$Default().pImageMemoryBarriers(barrier);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
    }

    private static final class StorageImage {
        private final VulkanDevice device;
        private final int width;
        private final int height;
        private final long image;
        private final long allocation;
        private final long view;
        private boolean initialized;
        private boolean destroyed;

        private StorageImage(VulkanDevice device, int width, int height) {
            this.device = device;
            this.width = width;
            this.height = height;
            long createdImage = 0L;
            long createdAllocation = 0L;
            long createdView = 0L;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(VK_FORMAT_R8G8B8A8_UNORM)
                        .mipLevels(1).arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
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
                        "Failed to create auto-exposure output image");
                createdImage = imageHandle.get(0);
                createdAllocation = allocationHandle.get(0);

                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default().image(createdImage)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(VK_FORMAT_R8G8B8A8_UNORM);
                viewInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1)
                        .baseArrayLayer(0).layerCount(1);
                LongBuffer viewHandle = stack.callocLong(1);
                VulkanUtils.crashIfFailure(device,
                        VK10.vkCreateImageView(device.vkDevice(), viewInfo, null, viewHandle),
                        "Failed to create auto-exposure output view");
                createdView = viewHandle.get(0);
            } catch (Throwable t) {
                if (createdView != 0L) VK10.vkDestroyImageView(device.vkDevice(), createdView, null);
                if (createdImage != 0L) Vma.vmaDestroyImage(device.vma(), createdImage, createdAllocation);
                throw t;
            }
            image = createdImage;
            allocation = createdAllocation;
            view = createdView;
            Vma.vmaSetAllocationName(device.vma(), allocation, "MGF auto exposure output");
        }

        private void destroy() {
            if (!destroyed) {
                destroyed = true;
                VK10.vkDestroyImageView(device.vkDevice(), view, null);
                Vma.vmaDestroyImage(device.vma(), image, allocation);
            }
        }
    }
}
