package dev.mgf.impl;

import net.fabricmc.api.ClientModInitializer;

import dev.mgf.api.internal.MgfRuntimeHolder;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.MgfRuntimeImpl;
import dev.mgf.impl.post.PostFxOverlays;

/**
 * MGF bootstrap. Installs the runtime into the API holder; everything else is
 * lazy and driven by the seams (mixins) as the game reaches them.
 */
public final class MgfClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MgfRuntimeImpl runtime = new MgfRuntimeImpl();
        MgfRuntimeHolder.set(runtime);
        PostFxOverlays.bootstrap();
        MgfConstants.LOGGER.info("MGF {} initialized (Minecraft 26.2 implementation)", runtime.version());
    }
}
