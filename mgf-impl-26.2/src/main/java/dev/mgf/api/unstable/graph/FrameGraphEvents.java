package dev.mgf.api.unstable.graph;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.impl.graph.FrameGraphDispatch;

/**
 * Registration point for frame-graph listeners (unstable surface — see
 * {@link FrameGraphContext} for the contract).
 *
 * <p>Anchors are best-effort except {@link FrameGraphAnchor#BEFORE_EXECUTE};
 * renderer-replacing mods may suppress per-pass anchors. Registration itself
 * is always safe, on any backend, at any time.
 *
 * <p>Scope note: these events fire while the frame graph is assembled — use
 * them to add or observe <i>passes</i>. To draw additional content inside
 * vanilla's existing passes, prefer Fabric API's {@code LevelRenderEvents},
 * which is designed for exactly that and composes fine with this API.
 */
public final class FrameGraphEvents {

    private FrameGraphEvents() {
    }

    public static void register(FrameGraphAnchor anchor, FrameGraphListener listener) {
        FrameGraphDispatch.register(anchor, listener);
    }
}
