package dev.mgf.api.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.mgf.api.framegen.FrameGenerationCapabilities;
import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationRequirements;
import dev.mgf.api.framegen.FrameGenerationSession;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.present.PresentBatch;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.present.PresentHookCapabilities;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscalerCapabilities;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerRequirements;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.api.upscale.UpscalerSupport;

final class ProviderSpiTest {

    @Test
    void oneRegistrarCanRegisterAllProviderRoles() {
        CollectingRegistry registry = new CollectingRegistry();
        MgfProviderRegistrar registrar = target -> {
            target.registerUpscaler(new FakeUpscaler());
            target.registerFrameGenerator(new FakeFrameGenerator());
            target.registerPresentHook(new FakePresentHook());
        };

        registrar.registerProviders(registry);

        assertEquals(List.of(new ProviderId("example:upscaler")),
                registry.upscalers.stream().map(provider -> provider.descriptor().id()).toList());
        assertEquals(List.of(new ProviderId("example:framegen")),
                registry.frameGenerators.stream().map(provider -> provider.descriptor().id()).toList());
        assertEquals(List.of(new ProviderId("example:present")),
                registry.presentHooks.stream().map(provider -> provider.descriptor().id()).toList());
    }

    @Test
    void requirementsAndCapabilitiesAreDefensivelyCopiedAndValidated() {
        Set<FrameResourceKind> required = new HashSet<>(Set.of(FrameResourceKind.COLOR));
        UpscalerRequirements requirements = new UpscalerRequirements(required, Set.of(FrameResourceKind.DEPTH));
        required.add(FrameResourceKind.MOTION_VECTORS);
        assertEquals(Set.of(FrameResourceKind.COLOR), requirements.requiredResources());

        assertThrows(IllegalArgumentException.class,
                () -> new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of(FrameResourceKind.COLOR)));
        assertThrows(IllegalArgumentException.class,
                () -> new UpscalerCapabilities(1.0, 0.5, Set.of(ColorEncoding.SRGB), Set.of("quality")));
        assertThrows(IllegalArgumentException.class,
                () -> new FrameGenerationCapabilities(Set.of(ColorEncoding.SRGB), Set.of(), 2));
    }

    @Test
    void presentBatchRequiresOneRealFrameAtTheEnd() {
        PresentFrame generated = presentFrame(PresentFrameKind.GENERATED, 0);
        PresentFrame real = presentFrame(PresentFrameKind.REAL, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new PresentBatch(List.of(generated)));
        assertThrows(IllegalArgumentException.class,
                () -> new PresentBatch(List.of(real, generated)));
        assertEquals(List.of(PresentFrameKind.GENERATED, PresentFrameKind.REAL),
                new PresentBatch(List.of(generated, real)).frames().stream()
                        .map(PresentFrame::kind).toList());
        assertEquals(PresentFrameKind.REAL,
                new PresentBatch(List.of(presentFrame(PresentFrameKind.REAL, 0))).frames().getFirst().kind());
    }

    private static PresentFrame presentFrame(PresentFrameKind kind, int ordinal) {
        FrameInfo info = new FrameInfo(1, 0.016, false, 1, 1);
        ImageState state = new ImageState(1, 0, 0, -1);
        BorrowedImage image = new BorrowedImage(10 + ordinal, 20 + ordinal,
                1920, 1080, 37, 7, ColorEncoding.SRGB, state,
                ImageOwnership.MGF, ImageLifetime.CALLBACK, 1, 1);
        return new PresentFrame(info, kind, ordinal, image, Optional.empty());
    }

    private static ProviderDescriptor descriptor(String id) {
        return new ProviderDescriptor(new ProviderId(id), id, "1.0.0", 0, 0, 3);
    }

    private static final class CollectingRegistry implements ProviderRegistry {
        private final List<UpscalerProvider> upscalers = new ArrayList<>();
        private final List<FrameGenerationProvider> frameGenerators = new ArrayList<>();
        private final List<PresentHookProvider> presentHooks = new ArrayList<>();

        @Override
        public void registerUpscaler(UpscalerProvider provider) {
            upscalers.add(provider);
        }

        @Override
        public void registerFrameGenerator(FrameGenerationProvider provider) {
            frameGenerators.add(provider);
        }

        @Override
        public void registerPresentHook(PresentHookProvider provider) {
            presentHooks.add(provider);
        }
    }

    private static final class FakeUpscaler implements UpscalerProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return ProviderSpiTest.descriptor("example:upscaler");
        }

        @Override
        public UpscalerSupport probe(ProviderEnvironment environment) {
            return UpscalerSupport.available(
                    new UpscalerCapabilities(0.5, 1.0,
                            Set.of(ColorEncoding.SRGB), Set.of("quality")),
                    new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
        }

        @Override
        public UpscalerSession open(ProviderSessionContext context) {
            return new UpscalerSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(UpscaleFrame frame) { return ProviderResult.success(); }
                @Override public void close() { }
            };
        }
    }

    private static final class FakeFrameGenerator implements FrameGenerationProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return ProviderSpiTest.descriptor("example:framegen");
        }

        @Override
        public FrameGenerationSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
            return FrameGenerationSupport.available(
                    new FrameGenerationCapabilities(
                            Set.of(ColorEncoding.SRGB), Set.of(new ProviderId("example:upscaler")), 1),
                    new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
        }

        @Override
        public FrameGenerationSession open(ProviderSessionContext context) {
            return new FrameGenerationSession() {
                @Override public void resize(FrameDimensions dimensions) { }
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult record(FrameGenerationFrame frame) { return ProviderResult.success(); }
                @Override public void close() { }
            };
        }
    }

    private static final class FakePresentHook implements PresentHookProvider {
        @Override
        public ProviderDescriptor descriptor() {
            return ProviderSpiTest.descriptor("example:present");
        }

        @Override
        public PresentHookSupport probe(
                ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator) {
            return PresentHookSupport.available(new PresentHookCapabilities(true));
        }

        @Override
        public PresentHookSession open(ProviderSessionContext context) {
            return new PresentHookSession() {
                @Override public void reset(ResetReason reason) { }
                @Override public ProviderResult beforePresent(PresentFrame frame) { return ProviderResult.success(); }
                @Override public void afterPresent(PresentReceipt receipt) { }
                @Override public void close() { }
            };
        }
    }
}
