package dev.mgf.impl.upscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.upscale.UpscalerCapabilities;

final class RenderResolutionControllerTest {

    @Test
    void selectsOnlyAdapterSupportedScales() {
        UpscalerCapabilities capabilities = new UpscalerCapabilities(
                0.5, 1.0,
                Set.of(0.5, 2.0 / 3.0, 0.75, 1.0),
                Set.of(ColorEncoding.SRGB),
                Set.of("quality"));

        assertEquals(2.0 / 3.0,
                RenderResolutionController.selectScale(capabilities, 0.7).orElseThrow(),
                0.0001);
        assertTrue(RenderResolutionController.supportsNativeScale(capabilities));
    }

    @Test
    void rejectsProviderWithoutNativeScaleForCurrentAdapterPath() {
        UpscalerCapabilities capabilities = new UpscalerCapabilities(
                0.5, 0.75,
                Set.of(0.5, 0.75),
                Set.of(ColorEncoding.SRGB),
                Set.of("quality"));

        assertEquals(0.75,
                RenderResolutionController.selectScale(capabilities, 1.0).orElseThrow(),
                0.0001);
        assertEquals(false, RenderResolutionController.supportsNativeScale(capabilities));
    }

    @Test
    void computesRenderAndDisplayDimensions() {
        FrameDimensions half = RenderResolutionController.dimensions(1920, 1080, 0.5);
        assertEquals(960, half.renderWidth());
        assertEquals(540, half.renderHeight());
        assertEquals(1920, half.displayWidth());
        assertEquals(1080, half.displayHeight());

        assertThrows(IllegalArgumentException.class,
                () -> RenderResolutionController.dimensions(1920, 1080, 0.6));
    }
}
