package dev.mgf.impl.provider;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderResultCode;

/** Ordered real-only or generated-then-real presentation state for one game frame. */
final class ProviderPresentState {

    private final boolean generated;
    private Throwable failure;

    private ProviderPresentState(boolean generated) {
        this.generated = generated;
    }

    static ProviderPresentState realOnly() {
        return new ProviderPresentState(false);
    }

    static ProviderPresentState fromFrameGeneration(ProviderResult result) {
        Objects.requireNonNull(result, "result");
        return new ProviderPresentState(result.code() == ProviderResultCode.SUCCESS);
    }

    boolean generated() {
        return generated;
    }

    Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    void present(Presenter presenter) {
        Objects.requireNonNull(presenter, "presenter");
        if (!generated) {
            presenter.presentCurrent(PresentFrameKind.REAL);
            return;
        }

        presenter.presentCurrent(PresentFrameKind.GENERATED);
        try {
            presenter.acquireReal();
        } catch (Throwable throwable) {
            failure = throwable;
            presenter.disableFrameGeneration(throwable);
            return;
        }
        presenter.restoreReal();
        presenter.blitAndSubmitReal();
        presenter.presentCurrent(PresentFrameKind.REAL);
    }

    interface Presenter {
        void presentCurrent(PresentFrameKind kind);

        void acquireReal() throws Exception;

        void restoreReal();

        void blitAndSubmitReal();

        void disableFrameGeneration(Throwable throwable);
    }
}
