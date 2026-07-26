package dev.mgf.impl.post;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.LevelTargetBundle;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphContext;
import dev.mgf.impl.graph.FrameGraphDispatch;

/**
 * Overlay storage and the frame-graph pass that draws them.
 *
 * <p>Draw pattern mirrors vanilla {@code PostPass}: open a render pass on the
 * main target's color view (no clear), set the pipeline, bind default
 * uniforms, draw 3 bufferless vertices ({@code draw(vertexCount=3,
 * instanceCount=1, firstVertex=0, firstInstance=0)}). The handle returned by
 * {@code readsAndWrites} replaces {@code targets.main} so downstream passes
 * order after the overlay — writing onto the imported external MainTarget is
 * what makes the result reach the swapchain (verified: after execute, vanilla
 * only clears the bundle and later blits the external target).
 */
public final class PostFxOverlays {

    private record Overlay(String name, RenderPipeline pipeline) {
    }

    private static final List<Overlay> OVERLAYS = new CopyOnWriteArrayList<>();
    private static volatile boolean bootstrapped;

    private PostFxOverlays() {
    }

    /** Called once from MGF bootstrap. */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        FrameGraphDispatch.register(FrameGraphAnchor.BEFORE_EXECUTE, PostFxOverlays::addOverlayPasses);
    }

    public static void add(String name, RenderPipeline pipeline) {
        OVERLAYS.add(new Overlay(name, pipeline));
    }

    private static void addOverlayPasses(FrameGraphContext context) {
        if (OVERLAYS.isEmpty()) {
            return;
        }
        LevelTargetBundle targets = context.targets();
        if (targets.main == null) {
            return;
        }
        for (Overlay overlay : OVERLAYS) {
            FramePass pass = context.builder().addPass("mgf_post_" + overlay.name());
            ResourceHandle<RenderTarget> handle = pass.readsAndWrites(targets.main);
            targets.main = handle;
            pass.executes(() -> draw(overlay, handle));
        }
    }

    private static void draw(Overlay overlay, ResourceHandle<RenderTarget> handle) {
        RenderTarget target = handle.get();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "mgf post: " + overlay.name(), target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(overlay.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.draw(3, 1, 0, 0);
        }
    }
}
