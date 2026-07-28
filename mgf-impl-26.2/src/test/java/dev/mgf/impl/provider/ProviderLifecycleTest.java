package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ResetReason;

final class ProviderLifecycleTest {

    private static final ProviderEnvironment ENVIRONMENT = new ProviderEnvironment(
            GraphicsBackendKind.VULKAN, 1, Optional.empty(), Set.of(FrameResourceKind.COLOR), true);

    @Test
    void deviceOpenRequestsOneFirstFrameReset() {
        ProviderRuntime runtime = runtime();

        runtime.open(ENVIRONMENT);

        assertEquals(Optional.of(ResetReason.FIRST_FRAME), runtime.applyPendingReset());
        assertTrue(runtime.applyPendingReset().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ResetReason.class)
    void everyLifecycleSignalMapsToItsExactResetReason(ResetReason reason) {
        ProviderRuntime runtime = runtime();
        runtime.open(ENVIRONMENT);
        runtime.applyPendingReset();

        runtime.requestReset(reason);

        assertEquals(Optional.of(reason), runtime.applyPendingReset());
    }

    @Test
    void duplicateSignalsCoalesceToOneStrongestReset() {
        ProviderRuntime runtime = runtime();
        runtime.open(ENVIRONMENT);
        runtime.applyPendingReset();

        runtime.requestReset(ResetReason.CAMERA_DISCONTINUITY);
        runtime.requestReset(ResetReason.RESOURCE_RELOAD);
        runtime.requestReset(ResetReason.RESIZE);
        runtime.requestReset(ResetReason.WORLD_CHANGE);
        runtime.requestReset(ResetReason.DIMENSION_CHANGE);
        runtime.requestReset(ResetReason.WORLD_CHANGE);

        assertEquals(Optional.of(ResetReason.DIMENSION_CHANGE), runtime.applyPendingReset());
        assertTrue(runtime.applyPendingReset().isEmpty());
    }

    @Test
    void deviceReplacementOverridesAllOtherPendingSignals() {
        ProviderRuntime runtime = runtime();
        runtime.open(ENVIRONMENT);
        runtime.applyPendingReset();

        runtime.requestReset(ResetReason.PROVIDER_CHANGE);
        runtime.requestReset(ResetReason.DEVICE_REPLACED);
        runtime.requestReset(ResetReason.DIMENSION_CHANGE);

        assertEquals(Optional.of(ResetReason.DEVICE_REPLACED), runtime.applyPendingReset());
    }

    private static ProviderRuntime runtime() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.freeze();
        return new ProviderRuntime(catalog, ProviderConfig.defaults(), () -> true);
    }
}
