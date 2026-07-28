package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.framegen.FrameGenerationCapabilities;
import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationRequirements;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderSessionState;
import dev.mgf.api.upscale.UpscalerCapabilities;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerRequirements;
import dev.mgf.api.upscale.UpscalerSupport;

final class ProviderSelectorTest {

    private static final ProviderEnvironment ENVIRONMENT = new ProviderEnvironment(
            GraphicsBackendKind.VULKAN, 1, Optional.empty(), Set.of(FrameResourceKind.COLOR), true);

    @Test
    void autoSelectsHighestPriorityThenLexicalId() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:z", 50, true));
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:b", 100, true));
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:a", 100, true));
        catalog.freeze();

        ProviderSelector.SelectedUpscaler selected = ProviderSelector.selectUpscaler(
                catalog, ProviderConfig.defaults().upscaler(), ENVIRONMENT);

        assertEquals(new ProviderId("example:a"), selected.provider().orElseThrow().descriptor().id());
        assertEquals(ProviderSessionState.READY, selected.diagnostic().state());
    }

    @Test
    void exactUnsupportedAndOffRemainUnselected() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:unsupported", 100, false));
        catalog.freeze();

        ProviderSelector.SelectedUpscaler unsupported = ProviderSelector.selectUpscaler(
                catalog, ProviderConfig.Choice.exact(new ProviderId("example:unsupported")), ENVIRONMENT);
        ProviderSelector.SelectedUpscaler off = ProviderSelector.selectUpscaler(
                catalog, ProviderConfig.Choice.off(), ENVIRONMENT);

        assertTrue(unsupported.provider().isEmpty());
        assertEquals(ProviderSessionState.UNSUPPORTED, unsupported.diagnostic().state());
        assertTrue(off.provider().isEmpty());
        assertEquals(ProviderSessionState.OFF, off.diagnostic().state());
    }

    @Test
    void probeExceptionIsIsolatedAndNextCandidateWins() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.throwingUpscaler("example:broken", 200));
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:working", 100, true));
        catalog.freeze();

        ProviderSelector.SelectedUpscaler selected = ProviderSelector.selectUpscaler(
                catalog, ProviderConfig.defaults().upscaler(), ENVIRONMENT);

        assertEquals(new ProviderId("example:working"), selected.provider().orElseThrow().descriptor().id());
    }

    @Test
    void frameGeneratorReceivesSelectedUpscaler() {
        ProviderCatalog catalog = new ProviderCatalog();
        ProviderId upscaler = new ProviderId("example:upscaler");
        catalog.registerFrameGenerator(ProviderTestFixtures.frameGenerator("example:fg", 10, upscaler));
        catalog.freeze();

        ProviderSelector.SelectedFrameGenerator selected = ProviderSelector.selectFrameGenerator(
                catalog, ProviderConfig.defaults().frameGeneration(), ENVIRONMENT, Optional.of(upscaler));

        assertEquals(new ProviderId("example:fg"), selected.provider().orElseThrow().descriptor().id());
    }

    @Test
    void upscalerMustAcceptNativeScaleAndSrgbInput() {
        ProviderCatalog scaleCatalog = new ProviderCatalog();
        scaleCatalog.registerUpscaler(upscalerWithCapabilities(
                "example:wrong-scale", new UpscalerCapabilities(
                        0.5, 0.75, Set.of(ColorEncoding.SRGB), Set.of("quality"))));
        scaleCatalog.freeze();
        ProviderSelector.SelectedUpscaler wrongScale = ProviderSelector.selectUpscaler(
                scaleCatalog, ProviderConfig.defaults().upscaler(), ENVIRONMENT);
        assertTrue(wrongScale.provider().isEmpty());
        assertEquals("unsupported_scale", wrongScale.diagnostic().reasonCode());

        ProviderCatalog colorCatalog = new ProviderCatalog();
        colorCatalog.registerUpscaler(upscalerWithCapabilities(
                "example:wrong-color", new UpscalerCapabilities(
                        0.5, 1.0, Set.of(ColorEncoding.LINEAR), Set.of("quality"))));
        colorCatalog.freeze();
        ProviderSelector.SelectedUpscaler wrongColor = ProviderSelector.selectUpscaler(
                colorCatalog, ProviderConfig.defaults().upscaler(), ENVIRONMENT);
        assertTrue(wrongColor.provider().isEmpty());
        assertEquals("unsupported_color_encoding", wrongColor.diagnostic().reasonCode());
    }

    @Test
    void frameGeneratorRequiresSafeMultiPresentAndCompatibleSrgbPlan() {
        ProviderId selectedUpscaler = new ProviderId("example:selected-upscaler");
        ProviderCatalog unsafeSurfaceCatalog = new ProviderCatalog();
        unsafeSurfaceCatalog.registerFrameGenerator(frameGeneratorWithCapabilities(
                "example:unsafe-surface",
                new FrameGenerationCapabilities(
                        Set.of(ColorEncoding.SRGB), Set.of(selectedUpscaler), 1)));
        unsafeSurfaceCatalog.freeze();
        ProviderEnvironment unsafeSurface = new ProviderEnvironment(
                GraphicsBackendKind.VULKAN, 1, Optional.empty(), Set.of(FrameResourceKind.COLOR), false);

        ProviderSelector.SelectedFrameGenerator unsafe = ProviderSelector.selectFrameGenerator(
                unsafeSurfaceCatalog, ProviderConfig.defaults().frameGeneration(),
                unsafeSurface, Optional.of(selectedUpscaler));

        assertTrue(unsafe.provider().isEmpty());
        assertEquals("multi_present_unsupported", unsafe.diagnostic().reasonCode());

        ProviderCatalog incompatibleCatalog = new ProviderCatalog();
        incompatibleCatalog.registerFrameGenerator(frameGeneratorWithCapabilities(
                "example:incompatible",
                new FrameGenerationCapabilities(
                        Set.of(ColorEncoding.SRGB), Set.of(new ProviderId("example:other")), 1)));
        incompatibleCatalog.freeze();

        ProviderSelector.SelectedFrameGenerator incompatible = ProviderSelector.selectFrameGenerator(
                incompatibleCatalog, ProviderConfig.defaults().frameGeneration(),
                ENVIRONMENT, Optional.of(selectedUpscaler));

        assertTrue(incompatible.provider().isEmpty());
        assertEquals("incompatible_upscaler", incompatible.diagnostic().reasonCode());

        ProviderCatalog colorCatalog = new ProviderCatalog();
        colorCatalog.registerFrameGenerator(frameGeneratorWithCapabilities(
                "example:wrong-color",
                new FrameGenerationCapabilities(
                        Set.of(ColorEncoding.LINEAR), Set.of(selectedUpscaler), 1)));
        colorCatalog.freeze();

        ProviderSelector.SelectedFrameGenerator wrongColor = ProviderSelector.selectFrameGenerator(
                colorCatalog, ProviderConfig.defaults().frameGeneration(),
                ENVIRONMENT, Optional.of(selectedUpscaler));

        assertTrue(wrongColor.provider().isEmpty());
        assertEquals("unsupported_color_encoding", wrongColor.diagnostic().reasonCode());
    }

    private static UpscalerProvider upscalerWithCapabilities(String id, UpscalerCapabilities capabilities) {
        UpscalerProvider delegate = ProviderTestFixtures.upscaler(id, 100, true);
        return new UpscalerProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return delegate.descriptor();
            }
            @Override public UpscalerSupport probe(ProviderEnvironment environment) {
                return UpscalerSupport.available(capabilities,
                        new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
            }
            @Override public dev.mgf.api.upscale.UpscalerSession open(
                    dev.mgf.api.provider.ProviderSessionContext context) {
                return delegate.open(context);
            }
        };
    }

    private static FrameGenerationProvider frameGeneratorWithCapabilities(
            String id, FrameGenerationCapabilities capabilities) {
        FrameGenerationProvider delegate = ProviderTestFixtures.frameGenerator(
                id, 100, new ProviderId("example:selected-upscaler"));
        return new FrameGenerationProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return delegate.descriptor();
            }
            @Override public FrameGenerationSupport probe(
                    ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
                return FrameGenerationSupport.available(capabilities,
                        new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
            }
            @Override public dev.mgf.api.framegen.FrameGenerationSession open(
                    dev.mgf.api.provider.ProviderSessionContext context) {
                return delegate.open(context);
            }
        };
    }
}
