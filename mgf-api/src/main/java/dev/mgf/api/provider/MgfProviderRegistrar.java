package dev.mgf.api.provider;

/** Pure-Java contract loaded from the {@code mgf:providers} Fabric entrypoint. */
@FunctionalInterface
public interface MgfProviderRegistrar {

    void registerProviders(ProviderRegistry registry);
}
