package dev.mgf.impl.provider;

import java.util.Objects;

import dev.mgf.api.present.PresentFrameKind;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderResultCode;

/** Real-only presentation state for the Minecraft 26.2 adapter. */
final class ProviderPresentState {

    static ProviderPresentState realOnly() {
        return new ProviderPresentState();
    }

    static ProviderPresentState fromFrameGeneration(ProviderResult result) {
        Objects.requireNonNull(result, "result");
        if (result.code() == ProviderResultCode.SUCCESS) {
            throw new IllegalStateException(
                    "Minecraft 26.2 cannot safely present generated and real frames");
        }
        return realOnly();
    }

    void present(Presenter presenter) {
        Objects.requireNonNull(presenter, "presenter");
        presenter.presentCurrent(PresentFrameKind.REAL);
    }

    interface Presenter {
        void presentCurrent(PresentFrameKind kind);
    }
}
