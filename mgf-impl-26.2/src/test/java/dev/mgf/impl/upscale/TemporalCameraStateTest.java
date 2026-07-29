package dev.mgf.impl.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.FrameMatrices;
import dev.mgf.api.provider.Matrix4;
import dev.mgf.api.provider.ResetReason;

final class TemporalCameraStateTest {

    @Test
    void tracksPreviousCameraOnlyAfterTwoFrames() {
        TemporalCameraState state = new TemporalCameraState();
        FrameMatrices identity = matrices();

        assertTrue(state.update(identity).isEmpty());
        assertEquals(identity, state.update(identity).orElseThrow());
        assertEquals(identity, state.previous().orElseThrow());

        state.reset(ResetReason.CAMERA_DISCONTINUITY);
        assertTrue(state.current().isEmpty());
        assertEquals(ResetReason.CAMERA_DISCONTINUITY, state.lastResetReason());
    }

    private static FrameMatrices matrices() {
        Matrix4 identity = Matrix4.identity();
        return new FrameMatrices(identity, identity, identity, identity);
    }
}
