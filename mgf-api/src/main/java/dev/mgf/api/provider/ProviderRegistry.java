package dev.mgf.api.provider;

import dev.mgf.api.framegen.FrameGenerationProvider;
import dev.mgf.api.present.PresentHookProvider;
import dev.mgf.api.upscale.UpscalerProvider;

/** Registration target supplied to one {@link MgfProviderRegistrar}. */
public interface ProviderRegistry {

    void registerUpscaler(UpscalerProvider provider);

    void registerFrameGenerator(FrameGenerationProvider provider);

    void registerPresentHook(PresentHookProvider provider);
}
