package dev.mgf.impl.mixin;

import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.mgf.impl.core.SeamHealth;
import dev.mgf.impl.provider.ProviderFrameBridge;

/** Connects providers to Minecraft 26.2's final composed blit and present seam. */
@Mixin(Minecraft.class)
public abstract class MinecraftPresentMixin {

    @ModifyArg(
            method = "renderFrame(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"),
            index = 1,
            require = 0)
    private GpuTextureView mgf$preparePresentSource(GpuTextureView original) {
        SeamHealth.markEngaged(SeamHealth.Seam.FINAL_PRESENT_HOOK);
        return ProviderFrameBridge.beforeBlit(original);
    }

    @Redirect(
            method = "renderFrame(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"),
            require = 0)
    private void mgf$presentFrames(GpuSurface surface) {
        ProviderFrameBridge.present(surface);
    }
}
