package dev.mgf.api.present;

import java.util.Optional;

import dev.mgf.api.provider.ProviderDescriptor;
import dev.mgf.api.provider.ProviderEnvironment;
import dev.mgf.api.provider.ProviderId;
import dev.mgf.api.provider.ProviderSessionContext;

/** Factory and capability probe for a controlled presentation hook. */
public interface PresentHookProvider {

    ProviderDescriptor descriptor();

    PresentHookSupport probe(
            ProviderEnvironment environment, Optional<ProviderId> selectedFrameGenerator);

    PresentHookSession open(ProviderSessionContext context);
}
