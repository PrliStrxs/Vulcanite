package dev.mgf.impl.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.impl.graph.FrameGraphDispatch;

/**
 * Frame-graph event seam (design doc §7, seam #4 — MEDIUM fragility).
 *
 * <p>Vanilla builds a fresh {@link FrameGraphBuilder} as a local in
 * {@code render(...)} each frame (bytecode-verified on 26.2: sky → main →
 * [transparency chain] → clouds → weather → [outline chain] → always-on-top →
 * {@code execute}). There is no vanilla hook, so we inject after the pass-add
 * calls and before {@code execute}, capturing the builder local.
 *
 * <p>All injections are {@code require = 0} (fail-soft): a renderer-replacing
 * mod or a future drop silently disables individual anchors; BEFORE_EXECUTE is
 * the designed most-durable anchor.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private LevelTargetBundle targets;

    private static final String RENDER = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
    )
    private void mgf$afterSkyPass(CallbackInfo ci, @Local FrameGraphBuilder builder) {
        FrameGraphDispatch.dispatch(FrameGraphAnchor.AFTER_SKY, builder, this.targets);
    }

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V")
    )
    private void mgf$afterMainPass(CallbackInfo ci, @Local FrameGraphBuilder builder) {
        FrameGraphDispatch.dispatch(FrameGraphAnchor.AFTER_MAIN, builder, this.targets);
    }

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V")
    )
    private void mgf$afterWeatherPass(CallbackInfo ci, @Local FrameGraphBuilder builder) {
        FrameGraphDispatch.dispatch(FrameGraphAnchor.AFTER_WEATHER, builder, this.targets);
    }

    @Inject(
            method = RENDER,
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V")
    )
    private void mgf$beforeExecute(CallbackInfo ci, @Local FrameGraphBuilder builder) {
        FrameGraphDispatch.dispatch(FrameGraphAnchor.BEFORE_EXECUTE, builder, this.targets);
    }
}
