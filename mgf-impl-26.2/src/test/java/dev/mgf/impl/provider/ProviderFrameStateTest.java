package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.FrameDimensions;

final class ProviderFrameStateTest {

    private static final FrameDimensions FULL_HD = new FrameDimensions(1920, 1080, 1920, 1080);
    private static final FrameDimensions QHD = new FrameDimensions(2560, 1440, 2560, 1440);

    @Test
    void dimensionsDriveOneMonotonicResourceGenerationForEveryActiveRole() {
        ProviderFrameState state = new ProviderFrameState();
        state.openDevice(3);

        ProviderFrameState.Snapshot first = state.beginFrame(3, FULL_HD);
        ProviderFrameState.Snapshot unchanged = state.beginFrame(3, FULL_HD);
        ProviderFrameState.Snapshot resized = state.beginFrame(3, QHD);

        assertEquals(1, first.resourceGeneration());
        assertTrue(first.resized());
        assertEquals(1, unchanged.resourceGeneration());
        assertFalse(unchanged.resized());
        assertEquals(2, resized.resourceGeneration());
        assertTrue(resized.resized());
    }

    @Test
    void staleDeviceAndResourceGenerationsAreRejectedBeforeCallback() {
        ProviderFrameState state = new ProviderFrameState();
        state.openDevice(7);
        state.beginFrame(7, FULL_HD);
        state.validateCurrent(7, 1);
        state.beginFrame(7, QHD);

        assertThrows(IllegalStateException.class, () -> state.beginFrame(6, QHD));
        assertThrows(IllegalStateException.class, () -> state.validateCurrent(7, 1));
        assertThrows(IllegalStateException.class, () -> state.validateCurrent(6, 2));
        state.validateCurrent(7, 2);
    }
}
