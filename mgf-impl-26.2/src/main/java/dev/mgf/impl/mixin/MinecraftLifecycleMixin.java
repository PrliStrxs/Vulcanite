package dev.mgf.impl.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.mgf.api.provider.ResetReason;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.SeamHealth;
import dev.mgf.impl.provider.ProviderRuntime;

/** Queues temporal reset signals from Minecraft 26.2 client lifecycle changes. */
@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin {

    @Shadow
    public ClientLevel level;

    @Inject(
            method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
            at = @At("HEAD"),
            require = 0)
    private void mgf$beforeLevelChange(ClientLevel nextLevel, CallbackInfo ci) {
        ClientLevel previousLevel = level;
        if (previousLevel == nextLevel) {
            return;
        }
        ResetReason reason = previousLevel != null
                && nextLevel != null
                && !previousLevel.dimension().equals(nextLevel.dimension())
                ? ResetReason.DIMENSION_CHANGE
                : ResetReason.WORLD_CHANGE;
        request(reason);
    }

    @Inject(
            method = "setCameraEntity(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            require = 0)
    private void mgf$beforeCameraChange(Entity nextCamera, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.getCameraEntity() != nextCamera) {
            request(ResetReason.CAMERA_DISCONTINUITY);
        }
    }

    @Inject(
            method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("TAIL"),
            require = 0)
    private void mgf$afterLevelCleared(Screen screen, CallbackInfo ci) {
        request(ResetReason.WORLD_CHANGE);
    }

    private static void request(ResetReason reason) {
        try {
            SeamHealth.markEngaged(SeamHealth.Seam.CLIENT_LIFECYCLE_HOOK);
            ProviderRuntime.current().requestReset(reason);
        } catch (Throwable throwable) {
            MgfConstants.LOGGER.error("Provider lifecycle signal {} failed; continuing", reason, throwable);
        }
    }
}
