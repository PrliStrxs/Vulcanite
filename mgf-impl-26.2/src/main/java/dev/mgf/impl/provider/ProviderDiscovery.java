package dev.mgf.impl.provider;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

import dev.mgf.api.provider.MgfProviderRegistrar;
import dev.mgf.impl.core.MgfConstants;

/** Loads provider registrars while isolating failures to the registrar that caused them. */
public final class ProviderDiscovery {

    private ProviderDiscovery() {
    }

    public static ProviderCatalog discover() {
        List<MgfProviderRegistrar> registrars;
        try {
            registrars = FabricLoader.getInstance().getEntrypoints(
                    MgfConstants.ENTRYPOINT_PROVIDERS, MgfProviderRegistrar.class);
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("Provider entrypoint discovery failed; no providers will be registered", throwable);
            registrars = List.of();
        }
        return discover(registrars);
    }

    static ProviderCatalog discover(Iterable<MgfProviderRegistrar> registrars) {
        ProviderCatalog catalog = new ProviderCatalog();
        for (MgfProviderRegistrar registrar : registrars) {
            try {
                registrar.registerProviders(catalog);
            } catch (Throwable throwable) {
                MgfConstants.LOGGER.error("Provider registrar {} failed; continuing discovery",
                        registrar.getClass().getName(), throwable);
            }
        }
        catalog.freeze();
        return catalog;
    }
}
