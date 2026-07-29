package dev.mgf.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.mgf.api.MgfRuntime;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderSelection;
import dev.mgf.api.provider.ProviderSessionState;
import dev.mgf.api.provider.ProviderKind;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.impl.provider.ProviderFrameBridge;
import dev.mgf.samples.interop.SampleProviders;

/** Provider registration, fallback, frame-work, and shutdown smoke assertions. */
final class SmokeProviderProbe {

    private static final ProviderId UPSCALER =
            new ProviderId("mgf-sample-interop:diagnostic-upscaler");
    private static final ProviderId FRAME_GENERATOR =
            new ProviderId("mgf-sample-interop:diagnostic-frame-generator");
    private static final ProviderId PRESENT_HOOK =
            new ProviderId("mgf-sample-interop:diagnostic-present-hook");

    private SmokeProviderProbe() {
    }

    static List<SmokeComputeProbe.Check> run(MgfRuntime runtime, String expectedBackend) {
        List<SmokeComputeProbe.Check> checks = new ArrayList<>();
        boolean vulkan = "vulkan".equalsIgnoreCase(expectedBackend);
        boolean providersEnabled = Boolean.getBoolean("mgf.smoke.providers");
        String providerMode = System.getProperty("mgf.smoke.providerMode", "off");
        boolean experimentalFrameGeneration =
                Boolean.getBoolean("mgf.smoke.experimentalFrameGeneration");
        boolean outputMode = List.of("passthrough", "recoverable", "fatal").stream()
                .anyMatch(providerMode::equalsIgnoreCase);
        String unsupportedUpscalerReason = unsupportedUpscalerReason(providerMode);
        boolean upscalerExpectedActive = unsupportedUpscalerReason == null;

        checkSelection(checks, runtime.providers().upscaler(), UPSCALER,
                vulkan, providersEnabled, providerMode, unsupportedUpscalerReason);
        checkSelection(checks, runtime.providers().frameGeneration(), FRAME_GENERATOR,
                vulkan, providersEnabled, providerMode, null, experimentalFrameGeneration);
        checkSelection(checks, runtime.providers().presentHook(), PRESENT_HOOK,
                vulkan, providersEnabled, providerMode, null, experimentalFrameGeneration);

        ProviderFrameBridge.Diagnostics bridge = ProviderFrameBridge.diagnostics();
        checks.add(check(bridge.realPresents() > 0,
                "providerRealPresents=" + bridge.realPresents()));
        checks.add(check(vulkan && outputMode && upscalerExpectedActive
                        ? bridge.outputCopies() > 0
                        : bridge.outputCopies() == 0,
                "providerOutputCopies=" + bridge.outputCopies()
                        + " mode=" + providerMode));
        checks.add(check(bridge.extraPresents() == 0,
                "providerExtraPresents=" + bridge.extraPresents()));

        SampleProviders.Counters sample = SampleProviders.counters();
        if (vulkan && providersEnabled) {
            checks.add(check(bridge.activeFrames() > 0,
                    "providerActiveFrames=" + bridge.activeFrames()));
            checks.add(check(bridge.realPresents() == bridge.activeFrames(),
                    "providerRealPresentsMatchActiveFrames=" + bridge.realPresents()
                            + "/" + bridge.activeFrames()));
            checks.add(check(bridge.allocations() == (upscalerExpectedActive ? 4 : 0),
                    "providerAllocations=" + bridge.allocations()));
            checks.add(check(bridge.commandRecordings() > 0,
                    "providerCommandRecordings=" + bridge.commandRecordings()));
            checks.add(check(outputMode ? bridge.copies() > 0 : bridge.copies() == 0,
                    "providerInternalCopies=" + bridge.copies() + " mode=" + providerMode));
            checks.add(check(sample.upscalerOpens() == (upscalerExpectedActive ? 1 : 0),
                    "diagnosticUpscalerOpens=" + sample.upscalerOpens()));
            checks.add(check(sample.frameGeneratorOpens() == 0,
                    "diagnosticFrameGeneratorOpens=" + sample.frameGeneratorOpens()));
            checks.add(check(sample.presentHookOpens() == 1,
                    "diagnosticPresentHookOpens=" + sample.presentHookOpens()));
            checks.add(check(upscalerExpectedActive
                            ? sample.upscalerFrames() > 0
                            : sample.upscalerFrames() == 0,
                    "diagnosticUpscalerFrames=" + sample.upscalerFrames()));
            checks.add(check(outputMode && upscalerExpectedActive
                            ? sample.upscalerSuccesses() > 0
                            : sample.upscalerSuccesses() == 0,
                    "diagnosticUpscalerSuccesses=" + sample.upscalerSuccesses()));
            if ("recoverable".equalsIgnoreCase(providerMode) && upscalerExpectedActive) {
                checks.add(check(sample.upscalerRecoverableFailures() == 1
                                && sample.upscalerFatalFailures() == 0
                                && bridge.outputCopies() < sample.upscalerFrames(),
                        "diagnosticRecoverableFallback=" + sample.upscalerRecoverableFailures()
                                + " copies=" + bridge.outputCopies()
                                + "/" + sample.upscalerFrames()));
            } else if ("fatal".equalsIgnoreCase(providerMode) && upscalerExpectedActive) {
                checks.add(check(sample.upscalerFatalFailures() == 1
                                && sample.upscalerRecoverableFailures() == 0
                                && bridge.outputCopies() < bridge.activeFrames(),
                        "diagnosticFatalFallback=" + sample.upscalerFatalFailures()
                                + " copies=" + bridge.outputCopies()
                                + "/" + bridge.activeFrames()));
            } else {
                checks.add(check(sample.upscalerRecoverableFailures() == 0
                                && sample.upscalerFatalFailures() == 0,
                        "diagnosticInjectedFailures=" + sample.upscalerRecoverableFailures()
                                + "/" + sample.upscalerFatalFailures()));
            }
            checks.add(check(sample.frameGeneratorFrames() == 0,
                    "diagnosticFrameGeneratorFrames=" + sample.frameGeneratorFrames()
                            + " (expected while multi-present is unsupported)"));
            checks.add(check(sample.presentHookFrames() > 0,
                    "diagnosticPresentHookSkippedFrames=" + sample.presentHookFrames()));
            checks.add(check(sample.presentReceipts() > 0,
                    "diagnosticPresentReceipts=" + sample.presentReceipts()));
            checks.add(check((upscalerExpectedActive
                            ? sample.upscalerResizes() > 0
                            : sample.upscalerResizes() == 0)
                            && sample.frameGeneratorResizes() == 0,
                    "diagnosticResizes=" + sample.upscalerResizes() + "/"
                            + sample.frameGeneratorResizes()));
            checks.add(check((upscalerExpectedActive
                            ? sample.upscalerResets() > 0
                            : sample.upscalerResets() == 0)
                            && sample.frameGeneratorResets() == 0
                            && sample.presentHookResets() > 0,
                    "diagnosticResets=" + sample.upscalerResets() + "/"
                            + sample.frameGeneratorResets() + "/" + sample.presentHookResets()
                            + " reasons=" + sample.upscalerResetReasonNames() + "/"
                            + sample.frameGeneratorResetReasonNames() + "/"
                            + sample.presentHookResetReasonNames()));
            checks.add(check(("fatal".equalsIgnoreCase(providerMode)
                            || !upscalerExpectedActive
                            || sample.upscalerSawReset(ResetReason.RESOURCE_RELOAD))
                            && sample.presentHookSawReset(ResetReason.RESOURCE_RELOAD),
                    "diagnosticResourceReloadReset="
                            + sample.upscalerSawReset(ResetReason.RESOURCE_RELOAD) + "/"
                            + sample.presentHookSawReset(ResetReason.RESOURCE_RELOAD)));
            checks.add(check((!upscalerExpectedActive
                            || sample.upscalerSawReset(ResetReason.FIRST_FRAME))
                            && sample.presentHookSawReset(ResetReason.FIRST_FRAME),
                    "diagnosticFirstFrameReset="
                            + sample.upscalerSawReset(ResetReason.FIRST_FRAME) + "/"
                            + sample.presentHookSawReset(ResetReason.FIRST_FRAME)));
        } else {
            checks.add(check(bridge.activeFrames() == 0,
                    "providerActiveFrames=" + bridge.activeFrames()));
            checks.add(check(bridge.allocations() == 0,
                    "providerAllocations=" + bridge.allocations()));
            checks.add(check(bridge.commandRecordings() == 0,
                    "providerCommandRecordings=" + bridge.commandRecordings()));
            checks.add(check(bridge.copies() == 0,
                    "providerCopies=" + bridge.copies()));
            checks.add(check(sample.totalLifecycleCallbacks() == 0,
                    "diagnosticLifecycleCallbacks=" + sample.totalLifecycleCallbacks()));
        }
        return List.copyOf(checks);
    }

    static void writeShutdownResult(Path path, String expectedBackend) {
        SampleProviders.Counters sample = SampleProviders.counters();
        boolean sessionsExpected = "vulkan".equalsIgnoreCase(expectedBackend)
                && Boolean.getBoolean("mgf.smoke.providers");
        String providerMode = System.getProperty("mgf.smoke.providerMode", "off");
        boolean upscalerExpected = sessionsExpected && unsupportedUpscalerReason(providerMode) == null;
        boolean presentHookExpected = sessionsExpected;
        boolean passed = sessionsExpected
                ? sample.upscalerCloses() == (upscalerExpected ? 1 : 0)
                        && sample.frameGeneratorCloses() == 0
                        && sample.presentHookCloses() == (presentHookExpected ? 1 : 0)
                : sample.upscalerCloses() == 0
                        && sample.frameGeneratorCloses() == 0
                        && sample.presentHookCloses() == 0;
        List<String> lines = List.of(
                passed ? "PASS" : "FAIL",
                "providerSessionsExpected=" + sessionsExpected,
                "diagnosticCloses=" + sample.upscalerCloses() + "/"
                        + sample.frameGeneratorCloses() + "/" + sample.presentHookCloses());
        try {
            Files.write(path, lines);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write provider shutdown result", exception);
        }
    }

    private static void checkSelection(
            List<SmokeComputeProbe.Check> checks,
            ProviderSelection selection,
            ProviderId expectedId,
            boolean vulkan,
            boolean providersEnabled,
            String providerMode,
            String expectedUnsupportedReason) {
        checkSelection(checks, selection, expectedId, vulkan, providersEnabled, providerMode,
                expectedUnsupportedReason, false);
    }

    private static void checkSelection(
            List<SmokeComputeProbe.Check> checks,
            ProviderSelection selection,
            ProviderId expectedId,
            boolean vulkan,
            boolean providersEnabled,
            String providerMode,
            String expectedUnsupportedReason,
            boolean experimentalFrameGeneration) {
        checks.add(check(selection.registered().contains(expectedId),
                selection.kind() + " registered=" + selection.registered()));
        if (!vulkan) {
            checks.add(check(selection.state() == ProviderSessionState.UNSUPPORTED
                            && selection.reasonCode().equals("backend_not_vulkan"),
                    selection.kind() + " state=" + selection.state()
                            + " reason=" + selection.reasonCode()));
        } else if (providersEnabled) {
            if (selection.kind() == ProviderKind.FRAME_GENERATION) {
                String expectedReason = "nvidia-experimental-frame-generation".equalsIgnoreCase(providerMode)
                        && !experimentalFrameGeneration
                                ? "experimental_frame_generation_disabled"
                                : "multi_present_unsupported";
                checks.add(check(selection.state() == ProviderSessionState.UNSUPPORTED
                                && selection.reasonCode().equals(expectedReason),
                        selection.kind() + " state=" + selection.state()
                                + " reason=" + selection.reasonCode()));
            } else if (expectedUnsupportedReason != null) {
                checks.add(check(selection.state() == ProviderSessionState.UNSUPPORTED
                                && selection.reasonCode().equals(expectedUnsupportedReason),
                        selection.kind() + " state=" + selection.state()
                                + " reason=" + selection.reasonCode()));
            } else if (selection.kind() == ProviderKind.UPSCALER
                    && "fatal".equalsIgnoreCase(providerMode)) {
                checks.add(check(selection.state() == ProviderSessionState.DISABLED
                                && selection.reasonCode().equals("diagnostic_fatal"),
                        selection.kind() + " state=" + selection.state()
                                + " reason=" + selection.reasonCode()));
            } else {
                checks.add(check(selection.state() == ProviderSessionState.ACTIVE
                                && selection.selected().equals(java.util.Optional.of(expectedId)),
                        selection.kind() + " state=" + selection.state()
                                + " selected=" + selection.selected().orElse(null)));
            }
        } else {
            checks.add(check(selection.state() == ProviderSessionState.UNSUPPORTED
                            && selection.reasonCode().equals("development_disabled"),
                    selection.kind() + " state=" + selection.state()
                            + " reason=" + selection.reasonCode()));
        }
    }

    private static SmokeComputeProbe.Check check(boolean passed, String detail) {
        return new SmokeComputeProbe.Check(passed, detail);
    }

    private static String unsupportedUpscalerReason(String providerMode) {
        return switch (providerMode.toLowerCase(java.util.Locale.ROOT)) {
            case "requires-depth" -> "depth_unavailable";
            case "requires-motion-vectors" -> "motion_vectors_unavailable";
            case "requires-matrices" -> "matrices_unavailable";
            case "requires-depth-motion-matrices",
                    "requires-all-temporal-upscaling-inputs" -> "depth_unavailable";
            default -> null;
        };
    }
}
