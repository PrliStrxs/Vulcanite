package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ProviderCatalogTest {

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
}
