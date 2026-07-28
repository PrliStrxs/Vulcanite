package dev.mgf.impl;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.blaze3d.systems.RenderSystem;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import dev.mgf.api.internal.MgfRuntimeHolder;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.MgfRuntimeImpl;
import dev.mgf.impl.compute.ComputeAutoExposureRegistry;
import dev.mgf.impl.post.PostFxOverlays;
import dev.mgf.impl.provider.ProviderCatalog;
import dev.mgf.impl.provider.ProviderConfig;
import dev.mgf.impl.provider.ProviderDiscovery;
import dev.mgf.impl.provider.ProviderFrameBridge;
import dev.mgf.impl.provider.ProviderRuntime;
import dev.mgf.impl.vk.VulkanDeviceAccess;

/**
 * MGF bootstrap. Installs the runtime into the API holder; everything else is
 * lazy and driven by the seams (mixins) as the game reaches them.
 */
public final class MgfClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ProviderCatalog catalog = ProviderDiscovery.discover();
        ProviderConfig config = loadProviderConfig();
        ProviderRuntime providerRuntime = new ProviderRuntime(catalog, config, RenderSystem::isOnRenderThread);
        ProviderRuntime.install(providerRuntime);
        MgfRuntimeImpl runtime = new MgfRuntimeImpl(providerRuntime);
        MgfRuntimeHolder.set(runtime);
        try {
            VulkanDeviceAccess.current().ifPresent(ProviderFrameBridge::onDeviceCreated);
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("Existing graphics device provider attachment failed; continuing", throwable);
        }
        ComputeAutoExposureRegistry.bootstrap();
        PostFxOverlays.bootstrap();
        MgfConstants.LOGGER.info("MGF {} initialized (Minecraft 26.2 implementation)", runtime.version());
    }

    private static ProviderConfig loadProviderConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("mgf-providers.properties");
        try {
            return ProviderConfig.load(path);
        } catch (IOException exception) {
            MgfConstants.LOGGER.warn("Could not read {}; using automatic provider selection", path, exception);
            return ProviderConfig.defaults();
        }
    }
}
