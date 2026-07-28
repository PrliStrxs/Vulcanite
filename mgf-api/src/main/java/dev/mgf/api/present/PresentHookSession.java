package dev.mgf.api.present;

import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ResetReason;

/** Render-thread callbacks around MGF-owned presentation. */
public interface PresentHookSession extends AutoCloseable {

    void reset(ResetReason reason);

    ProviderResult beforePresent(PresentFrame frame);

    void afterPresent(PresentReceipt receipt);

    @Override
    void close();
}
