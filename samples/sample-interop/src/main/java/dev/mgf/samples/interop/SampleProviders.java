package dev.mgf.samples.interop;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import dev.mgf.api.framegen.FrameGenerationCapabilities;
import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationMode;
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
    public static final String MODE_PROPERTY = "mgf.sample.providerMode";

    private static final ProviderId UPSCALER_ID = new ProviderId("mgf-sample-interop:diagnostic-upscaler");
    private static final ProviderId FRAME_GENERATOR_ID =
            new ProviderId("mgf-sample-interop:diagnostic-frame-generator");
    private static final ProviderId PRESENT_HOOK_ID =
            new ProviderId("mgf-sample-interop:diagnostic-present-hook");
    private static final AtomicLong UPSCALER_FRAMES = new AtomicLong();
    private static final AtomicLong UPSCALER_SUCCESSES = new AtomicLong();
    private static final AtomicLong UPSCALER_RECOVERABLE_FAILURES = new AtomicLong();
    private static final AtomicLong UPSCALER_FATAL_FAILURES = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_FRAMES = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_FRAMES = new AtomicLong();
    private static final AtomicLong PRESENT_RECEIPTS = new AtomicLong();
    private static final AtomicLong UPSCALER_OPENS = new AtomicLong();
    private static final AtomicLong UPSCALER_RESIZES = new AtomicLong();
    private static final AtomicLong UPSCALER_RESETS = new AtomicLong();
    private static final AtomicLong UPSCALER_RESET_REASONS = new AtomicLong();
    private static final AtomicLong UPSCALER_CLOSES = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_OPENS = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_RESIZES = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_RESETS = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_RESET_REASONS = new AtomicLong();
    private static final AtomicLong FRAME_GENERATOR_CLOSES = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_OPENS = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_RESETS = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_RESET_REASONS = new AtomicLong();
    private static final AtomicLong PRESENT_HOOK_CLOSES = new AtomicLong();

    @Override
    public void registerProviders(ProviderRegistry registry) {
        registry.registerUpscaler(new DiagnosticUpscaler());
        registry.registerFrameGenerator(new DiagnosticFrameGenerator());
        registry.registerPresentHook(new DiagnosticPresentHook());
    }

    public static Counters counters() {
        return new Counters(
                UPSCALER_FRAMES.get(),
                UPSCALER_SUCCESSES.get(),
                UPSCALER_RECOVERABLE_FAILURES.get(),
                UPSCALER_FATAL_FAILURES.get(),
                FRAME_GENERATOR_FRAMES.get(),
                PRESENT_HOOK_FRAMES.get(),
                PRESENT_RECEIPTS.get(),
                UPSCALER_OPENS.get(),
                UPSCALER_RESIZES.get(),
                UPSCALER_RESETS.get(),
                UPSCALER_RESET_REASONS.get(),
                UPSCALER_CLOSES.get(),
                FRAME_GENERATOR_OPENS.get(),
                FRAME_GENERATOR_RESIZES.get(),
                FRAME_GENERATOR_RESETS.get(),
                FRAME_GENERATOR_RESET_REASONS.get(),
                FRAME_GENERATOR_CLOSES.get(),
                PRESENT_HOOK_OPENS.get(),
                PRESENT_HOOK_RESETS.get(),
                PRESENT_HOOK_RESET_REASONS.get(),
                PRESENT_HOOK_CLOSES.get());
    }

    private static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static ProviderDescriptor descriptor(ProviderId id, String displayName) {
        return new ProviderDescriptor(id, displayName, "1.0.0", 1, 0, 0);
    }

    private static final class DiagnosticUpscaler implements UpscalerProvider {

        @Override
        public ProviderDescriptor descriptor() {
            return SampleProviders.descriptor(UPSCALER_ID, "Diagnostic Upscaler");
        }

        @Override
        public UpscalerSupport probe(ProviderEnvironment environment) {
            if (!enabled()) {
                return UpscalerSupport.unavailable(
                        "development_disabled", "Enable " + ENABLE_PROPERTY + " for diagnostics");
            }
            Set<FrameResourceKind> requiredResources = requiredUpscalerResources(providerMode());
            return UpscalerSupport.available(
                    new UpscalerCapabilities(1.0, 1.0, Set.of(ColorEncoding.SRGB), Set.of("native")),
                    new UpscalerRequirements(requiredResources, Set.of()));
        }

        @Override
        public UpscalerSession open(ProviderSessionContext context) {
            UPSCALER_OPENS.incrementAndGet();
            VulkanPassthroughUpscaler passthrough = outputEnabled()
                    ? new VulkanPassthroughUpscaler(context) : null;
            return new UpscalerSession() {
                private boolean closed;

                @Override public void resize(FrameDimensions dimensions) {
                    UPSCALER_RESIZES.incrementAndGet();
                }
                @Override public void reset(ResetReason reason) {
                    UPSCALER_RESETS.incrementAndGet();
                    UPSCALER_RESET_REASONS.getAndUpdate(mask -> mask | resetMask(reason));
                }
                @Override public ProviderResult record(UpscaleFrame frame) {
                    long frameNumber = UPSCALER_FRAMES.incrementAndGet();
                    if (frameNumber == 2 && "recoverable".equalsIgnoreCase(providerMode())) {
                        UPSCALER_RECOVERABLE_FAILURES.incrementAndGet();
                        return ProviderResult.recoverable(
                                "diagnostic_recoverable", "Injected recoverable smoke failure");
                    }
                    if (frameNumber == 2 && "fatal".equalsIgnoreCase(providerMode())) {
                        UPSCALER_FATAL_FAILURES.incrementAndGet();
                        return ProviderResult.fatal("diagnostic_fatal", "Injected fatal smoke failure");
                    }
                    if (passthrough != null) {
                        passthrough.record(frame);
                        UPSCALER_SUCCESSES.incrementAndGet();
                        return ProviderResult.success();
                    }
                    return skipped();
                }
                @Override public void close() {
                    if (!closed) {
                        closed = true;
                        UPSCALER_CLOSES.incrementAndGet();
                    }
                }
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
                            Set.of(ColorEncoding.SRGB), Set.of(UPSCALER_ID), 1,
                            frameGenerationMode()),
                    new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
        }

        @Override
        public FrameGenerationSession open(ProviderSessionContext context) {
            FRAME_GENERATOR_OPENS.incrementAndGet();
            return new FrameGenerationSession() {
                private boolean closed;

                @Override public void resize(FrameDimensions dimensions) {
                    FRAME_GENERATOR_RESIZES.incrementAndGet();
                }
                @Override public void reset(ResetReason reason) {
                    FRAME_GENERATOR_RESETS.incrementAndGet();
                    FRAME_GENERATOR_RESET_REASONS.getAndUpdate(mask -> mask | resetMask(reason));
                }
                @Override public ProviderResult record(FrameGenerationFrame frame) {
                    FRAME_GENERATOR_FRAMES.incrementAndGet();
                    return skipped();
                }
                @Override public void close() {
                    if (!closed) {
                        closed = true;
                        FRAME_GENERATOR_CLOSES.incrementAndGet();
                    }
                }
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
            PRESENT_HOOK_OPENS.incrementAndGet();
            return new PresentHookSession() {
                private boolean closed;

                @Override public void reset(ResetReason reason) {
                    PRESENT_HOOK_RESETS.incrementAndGet();
                    PRESENT_HOOK_RESET_REASONS.getAndUpdate(mask -> mask | resetMask(reason));
                }
                @Override public ProviderResult beforePresent(PresentFrame frame) {
                    PRESENT_HOOK_FRAMES.incrementAndGet();
                    return skipped();
                }
                @Override public void afterPresent(PresentReceipt receipt) {
                    PRESENT_RECEIPTS.incrementAndGet();
                }
                @Override public void close() {
                    if (!closed) {
                        closed = true;
                        PRESENT_HOOK_CLOSES.incrementAndGet();
                    }
                }
            };
        }
    }

    private static ProviderResult skipped() {
        return ProviderResult.skipped("diagnostic_noop", "Diagnostic provider records no GPU work");
    }

    private static boolean outputEnabled() {
        return Set.of("passthrough", "recoverable", "fatal").stream()
                .anyMatch(providerMode()::equalsIgnoreCase);
    }

    private static Set<FrameResourceKind> requiredUpscalerResources(String mode) {
        return switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "requires-depth" -> Set.of(FrameResourceKind.COLOR, FrameResourceKind.DEPTH);
            case "requires-motion-vectors" -> Set.of(FrameResourceKind.COLOR, FrameResourceKind.MOTION_VECTORS);
            case "requires-matrices" -> Set.of(FrameResourceKind.COLOR, FrameResourceKind.MATRICES);
            case "requires-depth-motion-matrices" -> Set.of(
                    FrameResourceKind.COLOR,
                    FrameResourceKind.DEPTH,
                    FrameResourceKind.MOTION_VECTORS,
                    FrameResourceKind.MATRICES);
            case "requires-all-temporal-upscaling-inputs" -> Set.of(
                    FrameResourceKind.COLOR,
                    FrameResourceKind.DEPTH,
                    FrameResourceKind.MOTION_VECTORS,
                    FrameResourceKind.MATRICES,
                    FrameResourceKind.EXPOSURE,
                    FrameResourceKind.REACTIVE_MASK,
                    FrameResourceKind.TRANSPARENCY_MASK,
                    FrameResourceKind.UI_MASK);
            default -> Set.of(FrameResourceKind.COLOR);
        };
    }

    private static String providerMode() {
        return System.getProperty(MODE_PROPERTY, "noop");
    }

    private static FrameGenerationMode frameGenerationMode() {
        return "nvidia-experimental-frame-generation".equalsIgnoreCase(providerMode())
                ? FrameGenerationMode.NVIDIA_EXPERIMENTAL
                : FrameGenerationMode.STANDARD;
    }

    private static long resetMask(ResetReason reason) {
        return 1L << reason.ordinal();
    }

    public record Counters(
            long upscalerFrames,
            long upscalerSuccesses,
            long upscalerRecoverableFailures,
            long upscalerFatalFailures,
            long frameGeneratorFrames,
            long presentHookFrames,
            long presentReceipts,
            long upscalerOpens,
            long upscalerResizes,
            long upscalerResets,
            long upscalerResetReasons,
            long upscalerCloses,
            long frameGeneratorOpens,
            long frameGeneratorResizes,
            long frameGeneratorResets,
            long frameGeneratorResetReasons,
            long frameGeneratorCloses,
            long presentHookOpens,
            long presentHookResets,
            long presentHookResetReasons,
            long presentHookCloses) {

        public boolean upscalerSawReset(ResetReason reason) {
            return (upscalerResetReasons & resetMask(reason)) != 0;
        }

        public boolean frameGeneratorSawReset(ResetReason reason) {
            return (frameGeneratorResetReasons & resetMask(reason)) != 0;
        }

        public boolean presentHookSawReset(ResetReason reason) {
            return (presentHookResetReasons & resetMask(reason)) != 0;
        }

        public String upscalerResetReasonNames() {
            return resetReasonNames(upscalerResetReasons);
        }

        public String frameGeneratorResetReasonNames() {
            return resetReasonNames(frameGeneratorResetReasons);
        }

        public String presentHookResetReasonNames() {
            return resetReasonNames(presentHookResetReasons);
        }

        public long totalLifecycleCallbacks() {
            return upscalerFrames + frameGeneratorFrames + presentHookFrames + presentReceipts
                    + upscalerOpens + upscalerResizes + upscalerResets + upscalerCloses
                    + frameGeneratorOpens + frameGeneratorResizes + frameGeneratorResets
                    + frameGeneratorCloses + presentHookOpens + presentHookResets + presentHookCloses;
        }

        private static String resetReasonNames(long reasons) {
            return java.util.Arrays.stream(ResetReason.values())
                    .filter(reason -> (reasons & resetMask(reason)) != 0)
                    .map(Enum::name)
                    .toList()
                    .toString();
        }
    }
}
