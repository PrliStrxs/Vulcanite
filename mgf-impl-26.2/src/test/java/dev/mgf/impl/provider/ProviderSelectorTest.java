package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderSessionState;

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
}
