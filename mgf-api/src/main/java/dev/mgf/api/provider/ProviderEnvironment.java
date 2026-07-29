package dev.mgf.api.provider;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.mgf.api.GraphicsAdapterVendor;
import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.vk.VkInterop;

/** Live device and adapter capability snapshot used during provider probing. */
public record ProviderEnvironment(
        GraphicsBackendKind backend,
        long deviceGeneration,
        Optional<VkInterop> vulkanInterop,
        Set<FrameResourceKind> availableResources,
        boolean multiPresentSupported,
        GraphicsAdapterVendor adapterVendor) {

    public ProviderEnvironment {
        backend = Objects.requireNonNull(backend, "backend");
        if (deviceGeneration < 1) {
            throw new IllegalArgumentException("deviceGeneration must be positive");
        }
        vulkanInterop = Objects.requireNonNull(vulkanInterop, "vulkanInterop");
        availableResources = Set.copyOf(availableResources);
        adapterVendor = Objects.requireNonNull(adapterVendor, "adapterVendor");
    }

    public ProviderEnvironment(
            GraphicsBackendKind backend,
            long deviceGeneration,
            Optional<VkInterop> vulkanInterop,
            Set<FrameResourceKind> availableResources,
            boolean multiPresentSupported) {
        this(backend, deviceGeneration, vulkanInterop, availableResources,
                multiPresentSupported, GraphicsAdapterVendor.UNKNOWN);
    }
}
