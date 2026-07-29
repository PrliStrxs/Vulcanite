package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.framegen.FrameGenerationCapabilities;
import dev.mgf.api.framegen.FrameGenerationFrame;
import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.framegen.FrameGenerationRequirements;
import dev.mgf.api.framegen.FrameGenerationSession;
import dev.mgf.api.framegen.FrameGenerationSupport;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentHookCapabilities;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.provider.ColorEncoding;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ProviderSessionState;
import dev.mgf.api.provider.ResetReason;
import dev.mgf.api.upscale.UpscaleFrame;
import dev.mgf.api.upscale.UpscalerCapabilities;
import dev.mgf.api.upscale.UpscalerProvider;
import dev.mgf.api.upscale.UpscalerRequirements;
import dev.mgf.api.upscale.UpscalerSession;
import dev.mgf.api.upscale.UpscalerSupport;

final class ProviderRuntimeTest {

    private static final ProviderEnvironment ENVIRONMENT = new ProviderEnvironment(
            GraphicsBackendKind.VULKAN, 1, Optional.empty(), Set.of(FrameResourceKind.COLOR), true);
    private static final FrameDimensions DIMENSIONS = new FrameDimensions(1920, 1080, 1920, 1080);

    @Test
    void sessionsFollowExactLifecycleOrder() {
        List<String> events = new ArrayList<>();
        ProviderRuntime runtime = runtime(events, () -> true);

        runtime.open(ENVIRONMENT);
        runtime.resize(DIMENSIONS);
        runtime.reset(ResetReason.WORLD_CHANGE);
        runtime.close();
        runtime.close();

        assertEquals(List.of(
                "open-upscaler",
                "open-framegen",
                "open-present",
                "resize-upscaler",
                "resize-framegen",
                "reset-upscaler:WORLD_CHANGE",
                "reset-framegen:WORLD_CHANGE",
                "reset-present:WORLD_CHANGE",
                "close-present",
                "close-framegen",
                "close-upscaler"), events);
    }

    @Test
    void initialResizePreservesFirstFrameReset() {
        List<String> events = new ArrayList<>();
        ProviderRuntime runtime = runtime(events, () -> true);

        runtime.open(ENVIRONMENT);
        runtime.resize(DIMENSIONS);

        assertEquals(Optional.of(ResetReason.FIRST_FRAME), runtime.applyPendingReset());
        assertTrue(events.contains("reset-upscaler:FIRST_FRAME"));
        assertFalse(events.contains("reset-upscaler:RESIZE"));
    }

    @Test
    void upscalerFailureSkipsDependentFrameGeneratorAndUpdatesDiagnostics() {
        ProviderRuntime runtime = runtime(new ArrayList<>(), () -> true);
        runtime.open(ENVIRONMENT);
        AtomicBoolean frameGeneratorCalled = new AtomicBoolean();

        ProviderResult upscaler = runtime.invokeUpscaler(
                session -> ProviderResult.fatal("upscale_failed", "Upscaler failed"));
        ProviderResult frameGenerator = runtime.invokeFrameGenerator(session -> {
            frameGeneratorCalled.set(true);
            return ProviderResult.success();
        });

        assertEquals("upscale_failed", upscaler.reasonCode());
        assertEquals("upscaler_unavailable", frameGenerator.reasonCode());
        assertFalse(frameGeneratorCalled.get());
        assertEquals(ProviderSessionState.DISABLED, runtime.selections().upscaler().state());
        assertEquals("upscale_failed", runtime.selections().upscaler().reasonCode());
        assertEquals(ProviderSessionState.DISABLED, runtime.selections().frameGeneration().state());
        assertEquals("upscaler_unavailable", runtime.selections().frameGeneration().reasonCode());
    }

    @Test
    void presentFailureStillRunsVanillaPresent() {
        ProviderRuntime runtime = runtime(new ArrayList<>(), () -> true);
        runtime.open(ENVIRONMENT);
        AtomicInteger vanillaPresents = new AtomicInteger();

        runtime.present(
                session -> ProviderResult.fatal("hook_failed", "Present hook failed"),
                vanillaPresents::incrementAndGet);

        assertEquals(1, vanillaPresents.get());
        assertEquals(ProviderSessionState.DISABLED, runtime.selections().presentHook().state());
        assertEquals("hook_failed", runtime.selections().presentHook().reasonCode());
    }

    @Test
    void providerExceptionsAreContainedAndDisableTheRole() {
        ProviderRuntime runtime = runtime(new ArrayList<>(), () -> true);
        runtime.open(ENVIRONMENT);

        ProviderResult result = runtime.invokeUpscaler(session -> {
            throw new IllegalStateException("SDK failed");
        });

        assertEquals("provider_exception", result.reasonCode());
        assertEquals(ProviderSessionState.DISABLED, runtime.selections().upscaler().state());
    }

    @Test
    void activeRoleFlagsAndExternalFrameGenerationDisableAreStable() {
        ProviderRuntime runtime = runtime(new ArrayList<>(), () -> true);

        assertFalse(runtime.hasActiveProviders());
        runtime.open(ENVIRONMENT);

        assertTrue(runtime.hasActiveProviders());
        assertTrue(runtime.upscalerActive());
        assertTrue(runtime.frameGenerationActive());
        assertTrue(runtime.presentHookActive());

        RuntimeException failure = new RuntimeException("second acquire failed");
        runtime.disableFrameGeneration("surface_acquire_failed", failure);

        assertFalse(runtime.frameGenerationActive());
        assertEquals(ProviderSessionState.DISABLED, runtime.selections().frameGeneration().state());
        assertEquals("surface_acquire_failed", runtime.selections().frameGeneration().reasonCode());
        assertTrue(runtime.selections().frameGeneration().message().contains("second acquire failed"));
    }

    @Test
    void openGlMarksRegisteredRolesUnsupportedWithoutOpeningSessions() {
        List<String> events = new ArrayList<>();
        ProviderRuntime runtime = runtime(events, () -> true);
        ProviderEnvironment openGl = new ProviderEnvironment(
                GraphicsBackendKind.OPENGL, 1, Optional.empty(), Set.of(), false);

        runtime.open(openGl);

        assertFalse(runtime.hasActiveProviders());
        assertTrue(events.isEmpty());
        assertEquals(ProviderSessionState.UNSUPPORTED, runtime.selections().upscaler().state());
        assertEquals(ProviderSessionState.UNSUPPORTED, runtime.selections().frameGeneration().state());
        assertEquals(ProviderSessionState.UNSUPPORTED, runtime.selections().presentHook().state());
        assertEquals("backend_not_vulkan", runtime.selections().upscaler().reasonCode());
        assertEquals("backend_not_vulkan", runtime.selections().frameGeneration().reasonCode());
        assertEquals("backend_not_vulkan", runtime.selections().presentHook().reasonCode());
    }

    @Test
    void afterPresentExceptionIsContainedAndDisablesHook() {
        ProviderRuntime runtime = runtime(new ArrayList<>(), () -> true);
        runtime.open(ENVIRONMENT);

        runtime.afterPresent(session -> {
            throw new IllegalStateException("after failed");
        });

        assertFalse(runtime.presentHookActive());
        assertEquals("provider_exception", runtime.selections().presentHook().reasonCode());
    }

    @Test
    void lifecycleMethodsRequireTheRenderThread() {
        AtomicBoolean renderThread = new AtomicBoolean(true);
        ProviderRuntime runtime = runtime(new ArrayList<>(), renderThread::get);
        runtime.open(ENVIRONMENT);
        renderThread.set(false);

        assertThrows(IllegalStateException.class, () -> runtime.resize(DIMENSIONS));
        assertThrows(IllegalStateException.class, () -> runtime.reset(ResetReason.WORLD_CHANGE));
        assertThrows(IllegalStateException.class, runtime::close);
    }

    @Test
    void discoveryIsolatesBrokenRegistrarsAndFreezesCatalog() {
        ProviderCatalog catalog = ProviderDiscovery.discover(List.of(
                registry -> registry.registerUpscaler(ProviderTestFixtures.upscaler("example:first", 10, true)),
                registry -> { throw new IllegalStateException("broken registrar"); },
                registry -> registry.registerUpscaler(ProviderTestFixtures.upscaler("example:second", 20, true))));

        assertTrue(catalog.isFrozen());
        assertEquals(List.of(new ProviderId("example:first"), new ProviderId("example:second")),
                catalog.upscalers().stream().map(provider -> provider.descriptor().id()).toList());
    }

    private static ProviderRuntime runtime(List<String> events, java.util.function.BooleanSupplier renderThread) {
        ProviderCatalog catalog = new ProviderCatalog();
        ProviderId upscalerId = new ProviderId("example:upscaler");
        catalog.registerUpscaler(loggedUpscaler(events));
        catalog.registerFrameGenerator(loggedFrameGenerator(events, upscalerId));
        catalog.registerPresentHook(loggedPresentHook(events));
        catalog.freeze();
        return new ProviderRuntime(catalog, ProviderConfig.defaults(), renderThread);
    }

    private static UpscalerProvider loggedUpscaler(List<String> events) {
        return new UpscalerProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return ProviderTestFixtures.descriptor("example:upscaler", 30);
            }
            @Override public UpscalerSupport probe(ProviderEnvironment environment) {
                return UpscalerSupport.available(
                        new UpscalerCapabilities(1.0, 1.0, Set.of(ColorEncoding.SRGB), Set.of("native")),
                        new UpscalerRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
            }
            @Override public UpscalerSession open(ProviderSessionContext context) {
                events.add("open-upscaler");
                return new UpscalerSession() {
                    @Override public void resize(FrameDimensions dimensions) { events.add("resize-upscaler"); }
                    @Override public void reset(ResetReason reason) { events.add("reset-upscaler:" + reason); }
                    @Override public ProviderResult record(UpscaleFrame frame) { return ProviderResult.success(); }
                    @Override public void close() { events.add("close-upscaler"); }
                };
            }
        };
    }

    private static FrameGenerationProvider loggedFrameGenerator(List<String> events, ProviderId upscalerId) {
        return new FrameGenerationProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return ProviderTestFixtures.descriptor("example:framegen", 20);
            }
            @Override public FrameGenerationSupport probe(
                    ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler) {
                if (!selectedUpscaler.equals(Optional.of(upscalerId))) {
                    return FrameGenerationSupport.unavailable("missing_upscaler", "Upscaler is required");
                }
                return FrameGenerationSupport.available(
                        new FrameGenerationCapabilities(Set.of(ColorEncoding.SRGB), Set.of(upscalerId), 1),
                        new FrameGenerationRequirements(Set.of(FrameResourceKind.COLOR), Set.of()));
            }
            @Override public FrameGenerationSession open(ProviderSessionContext context) {
                events.add("open-framegen");
                return new FrameGenerationSession() {
                    @Override public void resize(FrameDimensions dimensions) { events.add("resize-framegen"); }
                    @Override public void reset(ResetReason reason) { events.add("reset-framegen:" + reason); }
                    @Override public ProviderResult record(FrameGenerationFrame frame) { return ProviderResult.success(); }
                    @Override public void close() { events.add("close-framegen"); }
                };
            }
        };
    }

    private static PresentHookProvider loggedPresentHook(List<String> events) {
        return new PresentHookProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return ProviderTestFixtures.descriptor("example:present", 10);
            }
            @Override public PresentHookSupport probe(
                    ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator) {
                return PresentHookSupport.available(new PresentHookCapabilities(true));
            }
            @Override public PresentHookSession open(ProviderSessionContext context) {
                events.add("open-present");
                return new PresentHookSession() {
                    @Override public void reset(ResetReason reason) { events.add("reset-present:" + reason); }
                    @Override public ProviderResult beforePresent(PresentFrame frame) {
                        return ProviderResult.success();
                    }
                    @Override public void afterPresent(PresentReceipt receipt) { }
                    @Override public void close() { events.add("close-present"); }
                };
            }
        };
    }
}
