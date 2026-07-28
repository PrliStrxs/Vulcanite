package dev.mgf.impl.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderRegistry;
import dev.mgf.api.upscale.UpscalerProvider;

/** Mutable-at-bootstrap provider catalog that freezes before device probing. */
public final class ProviderCatalog implements ProviderRegistry {

    static final int API_MAJOR = 0;
    static final int API_MINOR = 3;

    private final Map<ProviderId, UpscalerProvider> upscalers = new LinkedHashMap<>();
    private final Map<ProviderId, FrameGenerationProvider> frameGenerators = new LinkedHashMap<>();
    private final Map<ProviderId, PresentHookProvider> presentHooks = new LinkedHashMap<>();
    private boolean frozen;

    @Override
    public synchronized void registerUpscaler(UpscalerProvider provider) {
        register(upscalers, Objects.requireNonNull(provider, "provider"), provider.descriptor(), "upscaler");
    }

    @Override
    public synchronized void registerFrameGenerator(FrameGenerationProvider provider) {
        register(frameGenerators, Objects.requireNonNull(provider, "provider"),
                provider.descriptor(), "frame generator");
    }

    @Override
    public synchronized void registerPresentHook(PresentHookProvider provider) {
        register(presentHooks, Objects.requireNonNull(provider, "provider"),
                provider.descriptor(), "present hook");
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    public synchronized List<UpscalerProvider> upscalers() {
        return List.copyOf(upscalers.values());
    }

    public synchronized List<FrameGenerationProvider> frameGenerators() {
        return List.copyOf(frameGenerators.values());
    }

    public synchronized List<PresentHookProvider> presentHooks() {
        return List.copyOf(presentHooks.values());
    }

    private <T> void register(
            Map<ProviderId, T> target, T provider, ProviderDescriptor descriptor, String role) {
        if (frozen) {
            throw new IllegalStateException("provider catalog is frozen");
        }
        validateDescriptor(descriptor);
        if (target.putIfAbsent(descriptor.id(), provider) != null) {
            throw new IllegalArgumentException("duplicate " + role + " provider ID: " + descriptor.id());
        }
    }

    private static void validateDescriptor(ProviderDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.minimumApiMajor() > API_MAJOR
                || descriptor.minimumApiMajor() == API_MAJOR
                && descriptor.minimumApiMinor() > API_MINOR) {
            throw new IllegalArgumentException("provider " + descriptor.id()
                    + " requires MGF API " + descriptor.minimumApiMajor() + "."
                    + descriptor.minimumApiMinor() + " or newer");
        }
    }
}
