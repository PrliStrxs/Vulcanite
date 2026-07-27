package dev.mgf.impl.compute;

import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_HOST_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_HOST_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_HOST_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier2;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.KHRPushDescriptor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;

import dev.mgf.api.unstable.compute.ComputeDispatcher;
import dev.mgf.impl.core.MgfConstants;

final class VulkanComputeDispatcher implements ComputeDispatcher {

    private final VulkanDevice device;
    private final long shaderCompiler;
    private final Set<VulkanComputeProgram> programs =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<VulkanComputeBuffer> buffers =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Runnable> deferredDestruction = new java.util.ArrayList<>();
    private boolean pendingWork;
    private boolean closed;

    VulkanComputeDispatcher(VulkanDevice device) {
        this.device = device;
        shaderCompiler = Shaderc.shaderc_compiler_initialize();
        if (shaderCompiler == 0L) {
            throw new IllegalStateException("shaderc_compiler_initialize returned null");
        }
    }

    @Override
    public Program createProgram(ProgramDescriptor descriptor) {
        assertUsable();
        VulkanComputeProgram program = new VulkanComputeProgram(this, descriptor);
        programs.add(program);
        return program;
    }

    @Override
    public Buffer createBuffer(BufferDescriptor descriptor) {
        assertUsable();
        VulkanComputeBuffer buffer = new VulkanComputeBuffer(this, descriptor);
        buffers.add(buffer);
        return buffer;
    }

    @Override
    public void dispatch(Dispatch command) {
        assertUsable();
        VulkanComputeProgram program = requireProgram(command.program());
        List<VulkanComputeBuffer> boundBuffers = command.storageBuffers().stream()
                .map(this::requireBuffer)
                .toList();
        VulkanCommandEncoder encoder = device.createCommandEncoder();
        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            bufferBarrier(commandBuffer, boundBuffers, stack,
                    VK_PIPELINE_STAGE_2_HOST_BIT_KHR | VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                    VK_ACCESS_2_HOST_WRITE_BIT_KHR | VK_ACCESS_2_MEMORY_READ_BIT_KHR
                            | VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            VK10.vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, program.pipeline());
            pushStorageBuffers(commandBuffer, program, boundBuffers, stack);
            VK10.vkCmdDispatch(commandBuffer,
                    command.groupCountX(), command.groupCountY(), command.groupCountZ());
            bufferBarrier(commandBuffer, boundBuffers, stack,
                    VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR,
                    VK_PIPELINE_STAGE_2_HOST_BIT_KHR | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                    VK_ACCESS_2_HOST_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR);
            VulkanUtils.crashIfFailure(device, VK10.vkEndCommandBuffer(commandBuffer),
                    "Failed to end compute command buffer " + program.label());
        }
        encoder.execute(commandBuffer);
        pendingWork = true;
    }

    @Override
    public void submitAndWait() {
        assertUsable();
        if (pendingWork) {
            device.createCommandEncoder().submit();
            device.graphicsQueue().waitIdle();
            pendingWork = false;
        }
        destroyTransientObjects();
    }

    VulkanDevice device() {
        return device;
    }

    long shaderCompiler() {
        return shaderCompiler;
    }

    void requireHostAccess(VulkanComputeBuffer buffer) {
        assertUsable();
        if (!buffer.belongsTo(this)) {
            throw new IllegalArgumentException("Compute buffer belongs to another dispatcher");
        }
        buffer.checkOpen();
        if (pendingWork) {
            throw new IllegalStateException("Call submitAndWait before host buffer access");
        }
    }

    void release(VulkanComputeProgram program, Runnable destroy) {
        programs.remove(program);
        deferOrDestroy(destroy);
    }

    void release(VulkanComputeBuffer buffer, Runnable destroy) {
        buffers.remove(buffer);
        deferOrDestroy(destroy);
    }

    void closeNow() {
        if (closed) {
            return;
        }
        closed = true;
        if (pendingWork) {
            closeStep("submit pending compute work", () -> device.createCommandEncoder().submit());
        }
        closeStep("wait for the graphics queue", () -> device.graphicsQueue().waitIdle());
        pendingWork = false;
        for (Runnable destroy : List.copyOf(deferredDestruction)) {
            closeStep("destroy a deferred compute resource", destroy);
        }
        deferredDestruction.clear();
        for (VulkanComputeProgram program : List.copyOf(programs)) {
            closeStep("destroy compute program " + program.label(), program::destroy);
        }
        for (VulkanComputeBuffer buffer : List.copyOf(buffers)) {
            closeStep("destroy compute buffer " + buffer.label(), buffer::destroy);
        }
        programs.clear();
        buffers.clear();
        closeStep("release shaderc compiler", () -> Shaderc.shaderc_compiler_release(shaderCompiler));
    }

    private static void pushStorageBuffers(VkCommandBuffer commandBuffer,
                                           VulkanComputeProgram program,
                                           List<VulkanComputeBuffer> boundBuffers,
                                           MemoryStack stack) {
        VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(boundBuffers.size(), stack);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(boundBuffers.size(), stack);
        for (int i = 0; i < boundBuffers.size(); i++) {
            VulkanComputeBuffer buffer = boundBuffers.get(i);
            bufferInfos.get(i).set(buffer.vkBuffer(), 0, buffer.size());
            writes.get(i)
                    .sType$Default()
                    .dstBinding(i)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.create(bufferInfos.get(i).address(), 1));
        }
        KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer,
                VK_PIPELINE_BIND_POINT_COMPUTE, program.pipelineLayout(), 0, writes);
    }

    private static void bufferBarrier(VkCommandBuffer commandBuffer,
                                      List<VulkanComputeBuffer> buffers,
                                      MemoryStack stack,
                                      long sourceStage, long sourceAccess,
                                      long destinationStage, long destinationAccess) {
        VkBufferMemoryBarrier2.Buffer barriers = VkBufferMemoryBarrier2.calloc(buffers.size(), stack);
        for (int i = 0; i < buffers.size(); i++) {
            VulkanComputeBuffer buffer = buffers.get(i);
            barriers.get(i)
                    .sType$Default()
                    .srcStageMask(sourceStage)
                    .srcAccessMask(sourceAccess)
                    .dstStageMask(destinationStage)
                    .dstAccessMask(destinationAccess)
                    .srcQueueFamilyIndex(-1)
                    .dstQueueFamilyIndex(-1)
                    .buffer(buffer.vkBuffer())
                    .offset(0)
                    .size(buffer.size());
        }
        VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pBufferMemoryBarriers(barriers);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
    }

    private VulkanComputeProgram requireProgram(Program program) {
        if (!(program instanceof VulkanComputeProgram vkProgram) || !vkProgram.belongsTo(this)) {
            throw new IllegalArgumentException("Compute program belongs to another dispatcher");
        }
        vkProgram.checkOpen();
        return vkProgram;
    }

    private VulkanComputeBuffer requireBuffer(Buffer buffer) {
        if (!(buffer instanceof VulkanComputeBuffer vkBuffer) || !vkBuffer.belongsTo(this)) {
            throw new IllegalArgumentException("Compute buffer belongs to another dispatcher");
        }
        vkBuffer.checkOpen();
        return vkBuffer;
    }

    private void deferOrDestroy(Runnable destroy) {
        if (pendingWork) {
            deferredDestruction.add(destroy);
        } else {
            destroy.run();
        }
    }

    private void destroyTransientObjects() {
        deferredDestruction.forEach(Runnable::run);
        deferredDestruction.clear();
    }

    private void assertUsable() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            throw new IllegalStateException("Compute dispatcher is closed");
        }
    }

    private static void closeStep(String action, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            MgfConstants.LOGGER.error("Failed to {} during compute shutdown; continuing", action, t);
        }
    }
}
