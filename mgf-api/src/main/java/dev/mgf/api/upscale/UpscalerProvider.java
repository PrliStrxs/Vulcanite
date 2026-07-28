package dev.mgf.api.upscale;

import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderSessionContext;

/** Factory and capability probe for one image upscaler implementation. */
public interface UpscalerProvider {

    ProviderDescriptor descriptor();

    UpscalerSupport probe(ProviderEnvironment environment);

    UpscalerSession open(ProviderSessionContext context);
}
