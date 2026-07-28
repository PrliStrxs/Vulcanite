package dev.mgf.api.framegen;

import java.util.Optional;

import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderSessionContext;

/** Factory and capability probe for one frame-generation implementation. */
public interface FrameGenerationProvider {

    ProviderDescriptor descriptor();

    FrameGenerationSupport probe(
            ProviderEnvironment environment, Optional<ProviderId> selectedUpscaler);

    FrameGenerationSession open(ProviderSessionContext context);
}
