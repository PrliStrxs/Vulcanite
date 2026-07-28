package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.provider.ProviderResult;

final class ProviderFrameBridgeTest {

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
        AtomicInteger pixelChangingCopies = new AtomicInteger();

        boolean copied = ProviderFrameBridge.copyOutputIfSuccessful(
                ProviderResult.skipped("diagnostic_noop", "Diagnostic provider records no GPU work"),
                pixelChangingCopies::incrementAndGet);

        assertFalse(copied);
        assertEquals(0, pixelChangingCopies.get());
    }

    @Test
    void successfulProviderCopiesOutputBackToMinecraftOnce() {
        AtomicInteger pixelChangingCopies = new AtomicInteger();

        boolean copied = ProviderFrameBridge.copyOutputIfSuccessful(
                ProviderResult.success(), pixelChangingCopies::incrementAndGet);

        assertTrue(copied);
        assertEquals(1, pixelChangingCopies.get());
    }

    @Test
    void realOnlyPerformsOnePresent() {
        FakePresenter presenter = new FakePresenter();

        ProviderPresentState.realOnly().present(presenter);

        assertEquals(List.of("present:REAL"), presenter.events);
    }

    @Test
    void generatedSuccessPresentsGeneratedThenReal() {
        FakePresenter presenter = new FakePresenter();

        ProviderPresentState.fromFrameGeneration(ProviderResult.success()).present(presenter);

        assertEquals(List.of(
                "present:GENERATED",
                "acquire-real",
                "restore-real",
                "blit-submit-real",
                "present:REAL"), presenter.events);
    }

    @Test
    void skippedFrameGenerationPerformsRealOnlyPresent() {
        FakePresenter presenter = new FakePresenter();

        ProviderPresentState.fromFrameGeneration(
                ProviderResult.skipped("history_unavailable", "History is not ready")).present(presenter);

        assertEquals(List.of("present:REAL"), presenter.events);
    }

    @Test
    void secondAcquireFailureDisablesFrameGenerationAndPreservesDiagnostic() {
        RuntimeException failure = new RuntimeException("second acquire failed");
        FakePresenter presenter = new FakePresenter();
        presenter.acquireFailure = failure;
        ProviderPresentState state = ProviderPresentState.fromFrameGeneration(ProviderResult.success());

        state.present(presenter);

        assertEquals(List.of("present:GENERATED", "acquire-real", "disable-framegen"), presenter.events);
        assertTrue(presenter.frameGenerationDisabled);
        assertSame(failure, presenter.disableReason);
        assertSame(failure, state.failure().orElseThrow());
        assertFalse(presenter.events.contains("present:REAL"));
    }

    private static final class FakePresenter implements ProviderPresentState.Presenter {
        private final List<String> events = new ArrayList<>();
        private RuntimeException acquireFailure;
        private boolean frameGenerationDisabled;
        private Throwable disableReason;

        @Override
        public void presentCurrent(PresentFrameKind kind) {
            events.add("present:" + kind);
        }

        @Override
        public void acquireReal() throws Exception {
            events.add("acquire-real");
            if (acquireFailure != null) {
                throw acquireFailure;
            }
        }

        @Override
        public void restoreReal() {
            events.add("restore-real");
        }

        @Override
        public void blitAndSubmitReal() {
            events.add("blit-submit-real");
        }

        @Override
        public void disableFrameGeneration(Throwable throwable) {
            events.add("disable-framegen");
            frameGenerationDisabled = true;
            disableReason = throwable;
        }
    }
}
