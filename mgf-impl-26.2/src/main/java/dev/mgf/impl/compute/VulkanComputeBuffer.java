package dev.mgf.impl.compute;

import static org.lwjgl.util.vma.Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT;
import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;

import dev.mgf.api.unstable.compute.ComputeDispatcher;

final class VulkanComputeBuffer implements ComputeDispatcher.Buffer {

    private final VulkanComputeDispatcher owner;
    private final VulkanDevice device;
    private final String label;
    private final int size;
    private final long vkBuffer;
    private final long allocation;
    private boolean closed;
    private boolean destroyed;

    VulkanComputeBuffer(VulkanComputeDispatcher owner, ComputeDispatcher.BufferDescriptor descriptor) {
        this.owner = owner;
        this.device = owner.device();
        this.label = descriptor.label();
        this.size = descriptor.size();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                            | VK_BUFFER_USAGE_TRANSFER_DST_BIT
                            | VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_HOST)
                    .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT);
            var bufferHandle = stack.callocLong(1);
            PointerBuffer allocationHandle = stack.callocPointer(1);
            VulkanUtils.crashIfFailure(device,
                    Vma.vmaCreateBuffer(device.vma(), bufferInfo, allocationInfo,
                            bufferHandle, allocationHandle, null),
                    "Failed to create compute storage buffer " + label);
            this.vkBuffer = bufferHandle.get(0);
            this.allocation = allocationHandle.get(0);
        }
        Vma.vmaSetAllocationName(device.vma(), allocation, label);
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void write(ByteBuffer source) {
        owner.requireHostAccess(this);
        if (source.remaining() > size) {
            throw new IllegalArgumentException("Source has " + source.remaining()
                    + " bytes, buffer capacity is " + size);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            VulkanUtils.crashIfFailure(device,
                    Vma.vmaMapMemory(device.vma(), allocation, pointer),
                    "Failed to map compute storage buffer " + label);
            try {
                ByteBuffer mapped = MemoryUtil.memByteBuffer(pointer.get(0), size);
                int length = source.remaining();
                mapped.put(0, source, source.position(), length);
                Vma.vmaFlushAllocation(device.vma(), allocation, 0, length);
            } finally {
                Vma.vmaUnmapMemory(device.vma(), allocation);
            }
        }
    }

    @Override
    public ByteBuffer read() {
        owner.requireHostAccess(this);
        ByteBuffer copy = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            VulkanUtils.crashIfFailure(device,
                    Vma.vmaMapMemory(device.vma(), allocation, pointer),
                    "Failed to map compute storage buffer " + label);
            try {
                Vma.vmaInvalidateAllocation(device.vma(), allocation, 0, size);
                copy.put(MemoryUtil.memByteBuffer(pointer.get(0), size));
                copy.flip();
                return copy;
            } finally {
                Vma.vmaUnmapMemory(device.vma(), allocation);
            }
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            owner.release(this, this::destroy);
        }
    }

    long vkBuffer() {
        checkOpen();
        return vkBuffer;
    }

    boolean belongsTo(VulkanComputeDispatcher dispatcher) {
        return owner == dispatcher;
    }

    void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Compute buffer is closed: " + label);
        }
    }

    void destroy() {
        if (!destroyed) {
            destroyed = true;
            Vma.vmaDestroyBuffer(device.vma(), vkBuffer, allocation);
        }
    }
}
