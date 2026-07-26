package dev.mgf.api.unstable.graph;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;

import net.minecraft.client.renderer.LevelTargetBundle;

import dev.mgf.api.graph.FrameGraphAnchor;

/**
 * Per-frame context handed to {@link FrameGraphListener}s.
 *
 * <p><b>Unstable surface:</b> this package ships in the per-drop implementation
 * artifact and exposes vanilla types directly ({@link FrameGraphBuilder},
 * {@link LevelTargetBundle}). Mojang can and does reshape these every drop;
 * code using this package must be prepared to update per Minecraft version.
 *
 * <p>{@code builder} is valid only for the duration of the callback — never
 * retain it across frames. {@code targets} holds vanilla's per-frame resource
 * handles (main/translucent/itemEntity/particles/weather/clouds/entityOutline);
 * handles may be null when the corresponding feature is inactive.
 */
public record FrameGraphContext(FrameGraphAnchor anchor,
                                FrameGraphBuilder builder,
                                LevelTargetBundle targets) {
}
