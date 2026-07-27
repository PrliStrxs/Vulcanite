package dev.mgf.impl.compute;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compute_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_2;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_spirv_version_1_5;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;

import dev.mgf.api.unstable.compute.ComputeDispatcher;

final class VulkanComputeProgram implements ComputeDispatcher.Program {

    private final VulkanComputeDispatcher owner;
    private final VulkanDevice device;
    private final String label;
    private final int bindings;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private final long pipeline;
    private boolean closed;
    private boolean destroyed;

    VulkanComputeProgram(VulkanComputeDispatcher owner, ComputeDispatcher.ProgramDescriptor descriptor) {
        this(owner, descriptor.label(), descriptor.glslSource(),
                java.util.Collections.nCopies(descriptor.storageBufferBindings(),
                        VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).stream().mapToInt(Integer::intValue).toArray());
    }

    VulkanComputeProgram(VulkanComputeDispatcher owner, String label, String source,
                         int... descriptorTypes) {
        this.owner = owner;
        this.device = owner.device();
        this.label = label;
        this.bindings = descriptorTypes.length;

        if (descriptorTypes.length == 0) {
            throw new IllegalArgumentException("A compute program needs at least one descriptor binding");
        }
        long createdDescriptorLayout = 0L;
        long createdPipelineLayout = 0L;
        long createdPipeline = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.calloc(bindings, stack);
            for (int i = 0; i < bindings; i++) {
                layoutBindings.get(i)
                        .binding(i)
                        .descriptorType(descriptorTypes[i])
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo descriptorLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
                    .pBindings(layoutBindings);
            var descriptorLayoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device,
                    VK10.vkCreateDescriptorSetLayout(device.vkDevice(), descriptorLayoutInfo,
                            null, descriptorLayoutHandle),
                    "Failed to create compute descriptor layout " + label);
            createdDescriptorLayout = descriptorLayoutHandle.get(0);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(createdDescriptorLayout));
            var pipelineLayoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device,
                    VK10.vkCreatePipelineLayout(device.vkDevice(), pipelineLayoutInfo,
                            null, pipelineLayoutHandle),
                    "Failed to create compute pipeline layout " + label);
            createdPipelineLayout = pipelineLayoutHandle.get(0);

            long shaderModule = compileShaderModule(owner.shaderCompiler(), label, source, stack);
            try {
                VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                        .sType$Default()
                        .layout(createdPipelineLayout)
                        .stage(stage -> stage
                                .sType$Default()
                                .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                                .module(shaderModule)
                                .pName(stack.UTF8("main")));
                var pipelineHandle = stack.callocLong(1);
                VulkanUtils.crashIfFailure(device,
                        VK10.vkCreateComputePipelines(device.vkDevice(), 0L, pipelineInfo,
                                null, pipelineHandle),
                        "Failed to create compute pipeline " + label);
                createdPipeline = pipelineHandle.get(0);
            } finally {
                VK10.vkDestroyShaderModule(device.vkDevice(), shaderModule, null);
            }
        } catch (Throwable t) {
            if (createdPipeline != 0L) {
                VK10.vkDestroyPipeline(device.vkDevice(), createdPipeline, null);
            }
            if (createdPipelineLayout != 0L) {
                VK10.vkDestroyPipelineLayout(device.vkDevice(), createdPipelineLayout, null);
            }
            if (createdDescriptorLayout != 0L) {
                VK10.vkDestroyDescriptorSetLayout(device.vkDevice(), createdDescriptorLayout, null);
            }
            throw t;
        }
        descriptorSetLayout = createdDescriptorLayout;
        pipelineLayout = createdPipelineLayout;
        pipeline = createdPipeline;
    }

    private long compileShaderModule(long compiler, String label, String source,
                                     MemoryStack stack) {
        long options = Shaderc.shaderc_compile_options_initialize();
        if (options == 0L) {
            throw new IllegalStateException("shaderc_compile_options_initialize returned null");
        }
        long result = 0L;
        try {
            Shaderc.shaderc_compile_options_set_target_env(
                    options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_target_spirv(options, shaderc_spirv_version_1_5);
            Shaderc.shaderc_compile_options_set_warnings_as_errors(options);
            result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    source,
                    shaderc_compute_shader,
                    label + ".comp",
                    "main",
                    options);
            if (result == 0L) {
                throw new IllegalArgumentException("shaderc returned no result for " + label);
            }
            if (Shaderc.shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new IllegalArgumentException("Compute shader " + label
                        + " failed to compile: " + Shaderc.shaderc_result_get_error_message(result));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result).order(ByteOrder.nativeOrder());
            VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(spirv);
            var moduleHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device,
                    VK10.vkCreateShaderModule(device.vkDevice(), moduleInfo, null, moduleHandle),
                    "Failed to create compute shader module " + label);
            return moduleHandle.get(0);
        } finally {
            if (result != 0L) {
                Shaderc.shaderc_result_release(result);
            }
            Shaderc.shaderc_compile_options_release(options);
        }
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public int storageBufferBindings() {
        return bindings;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            owner.release(this, this::destroy);
        }
    }

    boolean belongsTo(VulkanComputeDispatcher dispatcher) {
        return owner == dispatcher;
    }

    long descriptorSetLayout() {
        checkOpen();
        return descriptorSetLayout;
    }

    long pipelineLayout() {
        checkOpen();
        return pipelineLayout;
    }

    long pipeline() {
        checkOpen();
        return pipeline;
    }

    void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Compute program is closed: " + label);
        }
    }

    void destroy() {
        if (!destroyed) {
            destroyed = true;
            VK10.vkDestroyPipeline(device.vkDevice(), pipeline, null);
            VK10.vkDestroyPipelineLayout(device.vkDevice(), pipelineLayout, null);
            VK10.vkDestroyDescriptorSetLayout(device.vkDevice(), descriptorSetLayout, null);
        }
    }
}
