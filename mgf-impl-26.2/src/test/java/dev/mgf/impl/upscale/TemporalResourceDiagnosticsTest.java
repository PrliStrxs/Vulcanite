package dev.mgf.impl.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.upscale.ExposureMode;
import dev.mgf.api.upscale.UiCompositionHint;

final class TemporalResourceDiagnosticsTest {

    @Test
    void reportsOnlyVerifiedResourcesForTheCurrentAdapterPath() {
        assertEquals(Set.of(FrameResourceKind.COLOR),
                TemporalResourceDiagnostics.verifiedResources());
    }

    @Test
    void mapsMissingTemporalResourcesToStableReasonCodes() {
        assertEquals("depth_unavailable",
                TemporalResourceDiagnostics.reasonCode(FrameResourceKind.DEPTH));
        assertEquals("motion_vectors_unavailable",
                TemporalResourceDiagnostics.reasonCode(FrameResourceKind.MOTION_VECTORS));
        assertEquals("matrices_unavailable",
                TemporalResourceDiagnostics.reasonCode(FrameResourceKind.MATRICES));
        assertEquals("ui_composition_unavailable",
                TemporalResourceDiagnostics.reasonCode(FrameResourceKind.UI_MASK));
        assertEquals("exposure_unavailable",
                TemporalResourceDiagnostics.reasonCode(FrameResourceKind.EXPOSURE));

        var missing = TemporalResourceDiagnostics.firstMissingSupport(
                Set.of(FrameResourceKind.MATRICES, FrameResourceKind.DEPTH)).orElseThrow();
        assertEquals("depth_unavailable", missing.reasonCode());
        var missingExposure = TemporalResourceDiagnostics.firstMissingSupport(
                Set.of(FrameResourceKind.EXPOSURE)).orElseThrow();
        assertEquals("exposure_unavailable", missingExposure.reasonCode());
    }

    @Test
    void identityExposureHintsDoNotEnableVisualChanges() {
        var hints = TemporalResourceDiagnostics.identityExposureHints(true);
        assertTrue(hints.resetHistory());
        assertEquals(ExposureMode.IDENTITY, hints.exposureMode());
        assertEquals(UiCompositionHint.UI_ALREADY_IN_INPUT, hints.uiCompositionHint());
    }
}
