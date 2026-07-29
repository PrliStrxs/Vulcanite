package dev.mgf.impl.upscale;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderSupport;
import dev.mgf.api.upscale.ExposureMode;
import dev.mgf.api.upscale.TemporalUpscalingHints;
import dev.mgf.api.upscale.UiCompositionHint;

/** Truth table for temporal resources the 26.2 adapter can verify today. */
public final class TemporalResourceDiagnostics {

    private static final Set<FrameResourceKind> VERIFIED_26_2_RESOURCES =
            Set.of(FrameResourceKind.COLOR);

    private TemporalResourceDiagnostics() {
    }

    public static Set<FrameResourceKind> verifiedResources() {
        return VERIFIED_26_2_RESOURCES;
    }

    public static TemporalUpscalingHints identityExposureHints(boolean resetHistory) {
        return new TemporalUpscalingHints(
                resetHistory, 0.0F, 0.0F,
                ExposureMode.IDENTITY, UiCompositionHint.UI_ALREADY_IN_INPUT);
    }

    public static Optional<ProviderSupport> firstMissingSupport(Set<FrameResourceKind> missingResources) {
        Objects.requireNonNull(missingResources, "missingResources");
        return missingResources.stream()
                .min(Comparator.comparingInt(Enum::ordinal))
                .map(kind -> ProviderSupport.unsupported(reasonCode(kind), reasonMessage(kind)));
    }

    public static String reasonCode(FrameResourceKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case COLOR -> "low_res_color_unavailable";
            case DEPTH -> "depth_unavailable";
            case MOTION_VECTORS -> "motion_vectors_unavailable";
            case EXPOSURE -> "exposure_unavailable";
            case REACTIVE_MASK -> "reactive_mask_unavailable";
            case TRANSPARENCY_MASK -> "transparency_mask_unavailable";
            case OPTICAL_FLOW -> "optical_flow_unavailable";
            case UI_MASK -> "ui_composition_unavailable";
            case MATRICES -> "matrices_unavailable";
        };
    }

    private static String reasonMessage(FrameResourceKind kind) {
        return switch (kind) {
            case COLOR -> "Low-resolution scene color is not verified by this adapter path";
            case DEPTH -> "Depth is not verified for the current frame";
            case MOTION_VECTORS -> "Motion vectors are not verified for the current frame";
            case EXPOSURE -> "Exposure metadata or resource is not available";
            case REACTIVE_MASK -> "Reactive mask is not verified for the current frame";
            case TRANSPARENCY_MASK -> "Transparency mask is not verified for the current frame";
            case OPTICAL_FLOW -> "Optical flow is not provided by the core adapter";
            case UI_MASK -> "Native UI composition or UI mask is not verified for this provider";
            case MATRICES -> "Camera matrices are not verified for the current frame";
        };
    }
}
