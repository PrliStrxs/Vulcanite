package dev.mgf.impl.provider;

import java.util.Objects;
import java.util.function.Consumer;

import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.ResetReason;

/** Resize-safe owner for provider output, snapshot, and temporal-history images. */
final class VulkanProviderResources<T extends VulkanProviderResources.OwnedImage> implements AutoCloseable {

    private final long deviceGeneration;
    private final ImageFactory<T> factory;
    private final Consumer<Runnable> deferredDestroy;
    private Bundle<T> current;
    private long resourceGeneration;
    private boolean historyValid;
    private boolean closed;

    VulkanProviderResources(
            long deviceGeneration,
            ImageFactory<T> factory,
            Consumer<Runnable> deferredDestroy) {
        if (deviceGeneration < 1) {
            throw new IllegalArgumentException("deviceGeneration must be positive");
        }
        this.deviceGeneration = deviceGeneration;
        this.factory = Objects.requireNonNull(factory, "factory");
        this.deferredDestroy = Objects.requireNonNull(deferredDestroy, "deferredDestroy");
    }

    static VulkanProviderResources<VulkanProviderImage> create(
            VulkanDevice device, long deviceGeneration) {
        Objects.requireNonNull(device, "device");
        return new VulkanProviderResources<>(
                deviceGeneration,
                (label, width, height) -> new VulkanProviderImage(device, label, width, height),
                destroy -> device.createCommandEncoder().queueForDestroy(destroy::run));
    }

    void ensure(FrameDimensions dimensions) {
        Objects.requireNonNull(dimensions, "dimensions");
        ensureOpen();
        if (current != null && current.dimensions().equals(dimensions)) {
            return;
        }

        long nextGeneration = Math.addExact(resourceGeneration, 1);
        Bundle<T> replacement = createBundle(dimensions, nextGeneration);
        Bundle<T> retired = current;
        current = replacement;
        resourceGeneration = nextGeneration;
        historyValid = false;
        if (retired != null) {
            deferredDestroy.accept(retired::destroy);
        }
    }

    long deviceGeneration() {
        return deviceGeneration;
    }

    long resourceGeneration() {
        return resourceGeneration;
    }

    FrameDimensions dimensions() {
        return requireCurrent().dimensions();
    }

    T upscaledOutput() {
        return requireCurrent().upscaledOutput();
    }

    T realSnapshot() {
        return requireCurrent().realSnapshot();
    }

    T previousReal() {
        return requireCurrent().previousReal();
    }

    T generatedOutput() {
        return requireCurrent().generatedOutput();
    }

    boolean historyValid() {
        return historyValid;
    }

    void markHistoryValid() {
        requireCurrent();
        historyValid = true;
    }

    void reset(ResetReason reason) {
        Objects.requireNonNull(reason, "reason");
        historyValid = false;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        historyValid = false;
        Bundle<T> closing = current;
        current = null;
        if (closing != null) {
            closing.destroy();
        }
    }

    private Bundle<T> createBundle(FrameDimensions dimensions, long generation) {
        T upscaled = null;
        T snapshot = null;
        T previous = null;
        T generated = null;
        try {
            int width = dimensions.displayWidth();
            int height = dimensions.displayHeight();
            upscaled = factory.create("MGF provider upscaled output", width, height);
            snapshot = factory.create("MGF provider real snapshot", width, height);
            previous = factory.create("MGF provider previous real", width, height);
            generated = factory.create("MGF provider generated output", width, height);
            return new Bundle<>(dimensions, generation, upscaled, snapshot, previous, generated);
        } catch (Throwable throwable) {
            destroy(generated);
            destroy(previous);
            destroy(snapshot);
            destroy(upscaled);
            throw throwable;
        }
    }

    private Bundle<T> requireCurrent() {
        ensureOpen();
        if (current == null) {
            throw new IllegalStateException("provider resources are not allocated");
        }
        return current;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("provider resources are closed");
        }
    }

    private static void destroy(OwnedImage image) {
        if (image != null) {
            image.destroy();
        }
    }

    interface OwnedImage {
        void destroy();
    }

    @FunctionalInterface
    interface ImageFactory<T extends OwnedImage> {
        T create(String label, int width, int height);
    }

    private record Bundle<T extends OwnedImage>(
            FrameDimensions dimensions,
            long generation,
            T upscaledOutput,
            T realSnapshot,
            T previousReal,
            T generatedOutput) {

        private void destroy() {
            generatedOutput.destroy();
            previousReal.destroy();
            realSnapshot.destroy();
            upscaledOutput.destroy();
        }
    }
}
