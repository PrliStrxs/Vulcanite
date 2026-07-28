package dev.mgf.samples.interop;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
import dev.mgf.api.provider.MgfProviderRegistrar;
import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderRegistry;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscalerCapabilities;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerRequirements;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.api.upscale.UpscalerSupport;

/** Development-only no-op providers for discovery and lifecycle diagnostics. */
public final class SampleProviders implements MgfProviderRegistrar {

    public static final String ENABLE_PROPERTY = "mgf.sample.diagnosticProviders";

    private static final ProviderId UPSCALER_ID = new ProviderId("mgf-sample-interop:diagnostic-upscaler");
    private static final ProviderId FRAME_GENERATOR_ID =
            new ProviderId("mgf-sample-interop:diagnostic-frame-generator");
    private static final ProviderId PRESENT_HOOK_ID =
            new ProviderId("mgf-sample-interop:diagnostic-present-hook");
    private static final AtomicLong UPSCALER_FRAMES = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_FRAMES = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_FRAMES = new AtomicLong();
    private static final AtomicLong PRESENT_RECEIPTS = new AtomicLong();

    @Override
    public void registerProviders(ProviderRegistry registry) {
        registry.registerUpscaler(new DiagnosticUpscaler());
        registry.registerFrameGenerator(new DiagnosticFrameGenerator());
        registry.registerPresentHook(new DiagnosticPresentHook());
    }

    public static Counters counters() {
        return new Counters(
                UPSCALER_FRAMES.get(),
                FRAME_GENERATOR_FRAMES.get(),
                PRESENT_HOOK_FRAMES.get(),
                PRESENT_RECEIPTS.get());
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static ProviderDescriptor descriptor(ProviderId id, String displayName) {
        return new ProviderDescriptor(id, displayName, "0.3.0-alpha.1", 0, 0, 3);
    }

    private static final class DiagnosticUpscaler implements UpscalerProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return SampleProviders.descriptor(UPSCALER_ID, "Diagnostic No-op Upscaler");
        }

        @Override
        public UpscalerSupport probe(ProviderEnvironment environment) {
            if (!enabled()) {
                return UpscalerSupport.unavailable(
                        "development_disabled", "Enable " + ENABLE_PROPERTY + " for diagnostics");
            }
            return UpscalerSupport.available(
                    new UpscalerCapabilities(1.0, 1.0, Set.of(ColorEncoding.SRGB), Set.of("native")),
                    new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
        }

        @Override
        public UpscalerSession open(ProviderSessionContext context) {
            return new UpscalerSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(UpscaleFrame frame) {
                    UPSCALER_FRAMES.incrementAndGet();
                    return skipped();
                }
                @Override public void close() { }
            };
        }
    }

    private static final class DiagnosticFrameGenerator implements FrameGenerationProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return SampleProviders.descriptor(FRAME_GENERATOR_ID, "Diagnostic No-op Frame Generator");
        }

        @Override
        public FrameGenerationSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
            if (!enabled()) {
                return FrameGenerationSupport.unavailable(
                        "development_disabled", "Enable " + ENABLE_PROPERTY + " for diagnostics");
            }
            return FrameGenerationSupport.available(
                    new FrameGenerationCapabilities(
                            Set.of(ColorEncoding.SRGB), Set.of(UPSCALER_ID), 1),
                    new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
        }

        @Override
        public FrameGenerationSession open(ProviderSessionContext context) {
            return new FrameGenerationSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(FrameGenerationFrame frame) {
                    FRAME_GENERATOR_FRAMES.incrementAndGet();
                    return skipped();
                }
                @Override public void close() { }
            };
        }
    }

    private static final class DiagnosticPresentHook implements PresentHookProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return SampleProviders.descriptor(PRESENT_HOOK_ID, "Diagnostic No-op Present Hook");
        }

        @Override
        public PresentHookSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator) {
            if (!enabled()) {
                return PresentHookSupport.unavailable(
                        "development_disabled", "Enable " + ENABLE_PROPERTY + " for diagnostics");
            }
            return PresentHookSupport.available(new PresentHookCapabilities(true));
        }

        @Override
        public PresentHookSession open(ProviderSessionContext context) {
            return new PresentHookSession() {
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult beforePresent(PresentFrame frame) {
                    PRESENT_HOOK_FRAMES.incrementAndGet();
                    return skipped();
                }
                @Override public void afterPresent(PresentReceipt receipt) {
                    PRESENT_RECEIPTS.incrementAndGet();
                }
                @Override public void close() { }
            };
        }
    }

    private static ProviderResult skipped() {
        return ProviderResult.skipped("diagnostic_noop", "Diagnostic provider records no GPU work");
    }

    public record Counters(
            long upscalerFrames,
            long frameGeneratorFrames,
            long presentHookFrames,
            long presentReceipts) {
    }
}
