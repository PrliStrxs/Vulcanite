package dev.mgf.api.provider;

import java.util.Objects;

/** Immutable context supplied when MGF opens one provider device session. */
public record ProviderSessionContext(ProviderEnvironment environment) {

    public ProviderSessionContext {
        environment = Objects.requireNonNull(environment, "environment");
    }
}
