package dev.mgf.api.framegen;

import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ResetReason;

/** Render-thread device session for one selected frame generator. */
public interface FrameGenerationSession extends AutoCloseable {

    void resize(FrameDimensions dimensions);

    void reset(ResetReason reason);

    ProviderResult record(FrameGenerationFrame frame);

    @Override
    void close();
}
