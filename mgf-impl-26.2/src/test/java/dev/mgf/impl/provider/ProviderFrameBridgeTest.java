package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.present.PresentFrame;
import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.present.PresentHookCapabilities;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.present.PresentHookSession;
import dev.mgf.api.present.PresentHookSupport;
import dev.mgf.api.present.PresentReceipt;
import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.FrameResourceKind;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderSessionContext;
import dev.mgf.api.provider.ResetReason;

final class ProviderFrameBridgeTest {

    private static final FrameDimensions FULL_HD = new FrameDimensions(1920, 1080, 1920, 1080);
    private static final FrameDimensions QHD = new FrameDimensions(2560, 1440, 2560, 1440);

    @Test
    void providerFreeBeforeBlitReturnsOriginalWithoutWork() {
        Object original = new Object();
        AtomicInteger allocations = new AtomicInteger();
        AtomicInteger recordings = new AtomicInteger();
        AtomicInteger copies = new AtomicInteger();
        AtomicInteger descriptors = new AtomicInteger();

        Object result = ProviderFrameBridge.beforeBlit(original, false, view -> {
            allocations.incrementAndGet();
            recordings.incrementAndGet();
            copies.incrementAndGet();
            descriptors.incrementAndGet();
            return new Object();
        });

        assertSame(original, result);
        assertEquals(0, allocations.get());
        assertEquals(0, recordings.get());
        assertEquals(0, copies.get());
        assertEquals(0, descriptors.get());
    }

    @Test
    void skippedProviderDoesNotCopyOutputBackToMinecraft() {
        AtomicInteger outputCopies = new AtomicInteger();

        boolean copied = ProviderFrameBridge.copyOutputIfSuccessful(
                ProviderResult.skipped("diagnostic_noop", "Diagnostic provider records no GPU work"),
                outputCopies::incrementAndGet);

        assertFalse(copied);
        assertEquals(0, outputCopies.get());
    }

    @Test
    void successfulProviderCopiesOutputBackToMinecraftOnce() {
        AtomicInteger outputCopies = new AtomicInteger();

        boolean copied = ProviderFrameBridge.copyOutputIfSuccessful(
                ProviderResult.success(), outputCopies::incrementAndGet);

        assertTrue(copied);
        assertEquals(1, outputCopies.get());
    }

    @Test
    void realOnlyPerformsOnePresent() {
        FakePresenter presenter = new FakePresenter();

        ProviderPresentState.realOnly().present(presenter);

        assertEquals(List.of("present:REAL"), presenter.events);
    }

    @Test
    void generatedSuccessIsRejectedWithoutSafeMultiPresentSupport() {
        FakePresenter presenter = new FakePresenter();

        assertThrows(IllegalStateException.class,
                () -> ProviderPresentState.fromFrameGeneration(ProviderResult.success()));

        assertEquals(List.of(), presenter.events);
    }

    @Test
    void skippedFrameGenerationPerformsRealOnlyPresent() {
        FakePresenter presenter = new FakePresenter();

        ProviderPresentState.fromFrameGeneration(
                ProviderResult.skipped("history_unavailable", "History is not ready")).present(presenter);

        assertEquals(List.of("present:REAL"), presenter.events);
    }

    @Test
    void allocatedResourcesRequireDrainAfterLastProviderIsDisabled() {
        assertFalse(ProviderFrameBridge.requiresDeviceDrain(false, false, false));
        assertTrue(ProviderFrameBridge.requiresDeviceDrain(true, false, false));
        assertTrue(ProviderFrameBridge.requiresDeviceDrain(false, true, false));
        assertTrue(ProviderFrameBridge.requiresDeviceDrain(false, false, true));
    }

    @Test
    void presentHookOnlyResizeReceivesFirstFrameThenResizeReset() {
        List<ResetReason> resets = new ArrayList<>();
        ProviderRuntime runtime = presentHookOnlyRuntime(resets);
        ProviderFrameState frameState = new ProviderFrameState();
        frameState.openDevice(1);
        runtime.open(new ProviderEnvironment(
                GraphicsBackendKind.VULKAN, 1, Optional.empty(), Set.of(FrameResourceKind.COLOR), false));

        ProviderFrameBridge.notifyResize(
                frameState.beginFrame(1, FULL_HD), FULL_HD, runtime);
        assertEquals(Optional.of(ResetReason.FIRST_FRAME), runtime.applyPendingReset());

        ProviderFrameBridge.notifyResize(
                frameState.beginFrame(1, QHD), QHD, runtime);
        assertEquals(Optional.of(ResetReason.RESIZE), runtime.applyPendingReset());
        assertEquals(List.of(ResetReason.FIRST_FRAME, ResetReason.RESIZE), resets);
    }

    private static ProviderRuntime presentHookOnlyRuntime(List<ResetReason> resets) {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerPresentHook(new PresentHookProvider() {
            @Override public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return ProviderTestFixtures.descriptor("example:present", 10);
            }
            @Override public PresentHookSupport probe(
                    ProviderEnvironment environment,
                    Optional<dev.mgf.api.provider.ProviderId> selectedFrameGenerator) {
                return PresentHookSupport.available(new PresentHookCapabilities(true));
            }
            @Override public PresentHookSession open(ProviderSessionContext context) {
                return new PresentHookSession() {
                    @Override public void reset(ResetReason reason) { resets.add(reason); }
                    @Override public ProviderResult beforePresent(PresentFrame frame) {
                        return ProviderResult.skipped("test", "test");
                    }
                    @Override public void afterPresent(PresentReceipt receipt) { }
                    @Override public void close() { }
                };
            }
        });
        catalog.freeze();
        return new ProviderRuntime(catalog, ProviderConfig.defaults(), () -> true);
    }

    private static final class FakePresenter implements ProviderPresentState.Presenter {
        private final List<String> events = new ArrayList<>();

        @Override
        public void presentCurrent(PresentFrameKind kind) {
            events.add("present:" + kind);
        }
    }
}
