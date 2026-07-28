package dev.mgf.api.upscale;

import dev.mgf.api.provider.FrameDimensions;
import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ResetReason;

/** Render-thread device session for one selected upscaler. */
public interface UpscalerSession extends AutoCloseable {

    void resize(FrameDimensions dimensions);

    void reset(ResetReason reason);

    ProviderResult record(UpscaleFrame frame);

    @Override
    void close();
}
