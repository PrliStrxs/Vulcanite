package dev.mgf.impl.mixin;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.mgf.impl.core.SeamHealth;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.pipeline.PipelineWarmupRegistry;
import dev.mgf.impl.provider.ProviderRuntime;
import dev.mgf.api.provider.ResetReason;

/** Recompiles registered custom-source pipelines after vanilla clears its cache. */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {

    private static final String APPLY = "apply(Lnet/minecraft/client/renderer/ShaderManager$Configs;"
            + "Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V";

    @Inject(method = APPLY, at = @At("TAIL"), require = 0)
    private void mgf$warmUpRegisteredPipelines(
            ShaderManager.Configs configs,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        SeamHealth.markEngaged(SeamHealth.Seam.PIPELINE_RELOAD_HOOK);
        PipelineWarmupRegistry.warmUpRegistered();
        try {
            ProviderRuntime.current().requestReset(ResetReason.RESOURCE_RELOAD);
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("Provider resource-reload signal failed; continuing reload", throwable);
        }
    }
}
