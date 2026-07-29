package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProviderCatalogTest {

    @Test
    void acceptsOnePointZeroProviderDescriptors() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:one-zero", 0, true));

        assertEquals(1, catalog.upscalers().size());
    }

    @Test
    void rejectsFutureProviderDescriptors() {
        ProviderCatalog catalog = new ProviderCatalog();

        assertThrows(IllegalArgumentException.class,
                () -> catalog.registerUpscaler(upscalerWithApi("example:one-one", 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.registerUpscaler(upscalerWithApi("example:two-zero", 2, 0)));
    }

    @Test
    void rejectsDuplicateIdsWithinOneRole() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:same", 0, true));

        assertThrows(IllegalArgumentException.class,
                () -> catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:same", 1, true)));
    }

    @Test
    void permitsSameIdAcrossDifferentRolesAndFreezesCollections() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:same", 0, true));
        catalog.registerPresentHook(ProviderTestFixtures.presentHook("example:same", 0, false));
        catalog.freeze();

        assertEquals(1, catalog.upscalers().size());
        assertEquals(1, catalog.presentHooks().size());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.upscalers().clear());
        assertThrows(IllegalStateException.class,
                () -> catalog.registerUpscaler(ProviderTestFixtures.upscaler("example:late", 0, true)));
    }

    private static dev.mgf.api.upscale.UpscalerProvider upscalerWithApi(
            String id, int apiMajor, int apiMinor) {
        return new dev.mgf.api.upscale.UpscalerProvider() {
            @Override
            public dev.mgf.api.provider.ProviderDescriptor descriptor() {
                return ProviderTestFixtures.descriptor(id, 0, apiMajor, apiMinor);
            }

            @Override
            public dev.mgf.api.upscale.UpscalerSupport probe(
                    dev.mgf.api.provider.ProviderEnvironment environment) {
                throw new AssertionError("Future API providers should be rejected before probing");
            }

            @Override
            public dev.mgf.api.upscale.UpscalerSession open(
                    dev.mgf.api.provider.ProviderSessionContext context) {
                throw new AssertionError("Future API providers should be rejected before opening");
            }
        };
    }
}
