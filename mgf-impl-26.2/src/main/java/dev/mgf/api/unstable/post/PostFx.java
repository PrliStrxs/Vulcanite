package dev.mgf.api.unstable.post;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import dev.mgf.impl.post.PostFxOverlays;

/**
 * Minimal post-processing surface (unstable — vanilla types, ships in the
 * per-drop impl artifact).
 *
 * <p>An overlay is a full-screen triangle drawn onto the level's main render
 * target right before the frame graph executes, in registration order, world
 * rendering only. The pipeline should use vanilla's
 * {@code minecraft:core/screenquad} vertex shader (bufferless triangle via
 * {@code gl_VertexID}), a blend-enabled color target state, and no depth
 * state. Pipelines are compiled lazily by vanilla and survive resource
 * reloads and window resizes without any action here.
 */
public final class PostFx {

    private PostFx() {
    }

    /**
     * @param name short id used in the frame-graph pass label ({@code mgf_post_<name>})
     * @param pipeline the full-screen pipeline to draw with
     */
    public static void registerMainOverlay(String name, RenderPipeline pipeline) {
        PostFxOverlays.add(name, pipeline);
    }
}
