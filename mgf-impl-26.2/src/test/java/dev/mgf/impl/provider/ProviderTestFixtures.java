package dev.mgf.impl.provider;

import java.util.Optional;
import java.util.Set;

import dev.mgf.api.framegen.FrameGenerationCapabilities;
import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationRequirements;
import dev.mgf.api.framegen.FrameGenerationSession;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentHookCapabilities;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscalerCapabilities;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerRequirements;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.api.upscale.UpscalerSupport;

final class ProviderTestFixtures {

    private ProviderTestFixtures() {
    }

    static ProviderDescriptor descriptor(String id, int priority) {
        return descriptor(id, priority, 1, 0);
    }

    static ProviderDescriptor descriptor(String id, int priority, int apiMajor, int apiMinor) {
        return new ProviderDescriptor(new ProviderId(id), id, "1.0.0", priority, apiMajor, apiMinor);
    }

    static UpscalerProvider upscaler(String id, int priority, boolean supported) {
        return new UpscalerProvider() {
            @Override public ProviderDescriptor descriptor() { return ProviderTestFixtures.descriptor(id, priority); }
            @Override public UpscalerSupport probe(ProviderEnvironment environment) {
                return supported
                        ? UpscalerSupport.available(
                                new UpscalerCapabilities(0.5, 1.0,
                                        Set.of(ColorEncoding.SRGB), Set.of("quality")),
                                new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of()))
                        : UpscalerSupport.unavailable("unsupported", "Unsupported in test");
            }
            @Override public UpscalerSession open(ProviderSessionContext context) { return NOOP_UPSCALER; }
        };
    }

    static UpscalerProvider throwingUpscaler(String id, int priority) {
        return new UpscalerProvider() {
            @Override public ProviderDescriptor descriptor() { return ProviderTestFixtures.descriptor(id, priority); }
            @Override public UpscalerSupport probe(ProviderEnvironment environment) { throw new IllegalStateException("probe failed"); }
            @Override public UpscalerSession open(ProviderSessionContext context) { return NOOP_UPSCALER; }
        };
    }

    static FrameGenerationProvider frameGenerator(String id, int priority, ProviderId compatibleUpscaler) {
        return new FrameGenerationProvider() {
            @Override public ProviderDescriptor descriptor() { return ProviderTestFixtures.descriptor(id, priority); }
            @Override public FrameGenerationSupport probe(
                    ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
                if (selectedUpscaler.isEmpty() || !selectedUpscaler.get().equals(compatibleUpscaler)) {
                    return FrameGenerationSupport.unavailable("incompatible_upscaler", "Upscaler is incompatible");
                }
                return FrameGenerationSupport.available(
                        new FrameGenerationCapabilities(Set.of(ColorEncoding.SRGB), Set.of(compatibleUpscaler), 1),
                        new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
            }
            @Override public FrameGenerationSession open(ProviderSessionContext context) { return NOOP_FRAME_GENERATOR; }
        };
    }

    static PresentHookProvider presentHook(String id, int priority, boolean generated) {
        return new PresentHookProvider() {
            @Override public ProviderDescriptor descriptor() { return ProviderTestFixtures.descriptor(id, priority); }
            @Override public PresentHookSupport probe(
                    ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator) {
                return PresentHookSupport.available(new PresentHookCapabilities(generated));
            }
            @Override public PresentHookSession open(ProviderSessionContext context) { return NOOP_PRESENT; }
        };
    }

    private static final UpscalerSession NOOP_UPSCALER = new UpscalerSession() {
        @Override public void resize(FrameDimensions dimensions) { }
        @Override public void reset(ResetReason reason) { }
        @Override public ProviderResult record(UpscaleFrame frame) { return ProviderResult.skipped("test", "No output"); }
        @Override public void close() { }
    };

    private static final FrameGenerationSession NOOP_FRAME_GENERATOR = new FrameGenerationSession() {
        @Override public void resize(FrameDimensions dimensions) { }
        @Override public void reset(ResetReason reason) { }
        @Override public ProviderResult record(FrameGenerationFrame frame) { return ProviderResult.skipped("test", "No output"); }
        @Override public void close() { }
    };

    private static final PresentHookSession NOOP_PRESENT = new PresentHookSession() {
        @Override public void reset(ResetReason reason) { }
        @Override public ProviderResult beforePresent(PresentFrame frame) { return ProviderResult.success(); }
        @Override public void afterPresent(PresentReceipt receipt) { }
        @Override public void close() { }
    };
}
