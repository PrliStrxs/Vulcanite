package dev.mgf.impl.graph;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;

import net.minecraft.client.renderer.LevelTargetBundle;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphContext;
import dev.mgf.api.unstable.graph.FrameGraphListener;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.SeamHealth;

/**
 * Listener storage and dispatch for {@code FrameGraphEvents}. Called from
 * {@code LevelRendererMixin} on the render thread, once per anchor per frame
 * while a level is rendered.
 */
public final class FrameGraphDispatch {

    private static final Map<FrameGraphAnchor, List<FrameGraphListener>> LISTENERS = new EnumMap<>(FrameGraphAnchor.class);
    private static final Map<FrameGraphAnchor, List<FrameGraphListener>> LAST_LISTENERS =
            new EnumMap<>(FrameGraphAnchor.class);
    /** Listeners that threw — muted for the session so one bad mod cannot spam or stall the render loop. */
    private static final Set<FrameGraphListener> MUTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<FrameGraphAnchor> FIRED = EnumSet.noneOf(FrameGraphAnchor.class);

    static {
        for (FrameGraphAnchor anchor : FrameGraphAnchor.values()) {
            LISTENERS.put(anchor, new CopyOnWriteArrayList<>());
            LAST_LISTENERS.put(anchor, new CopyOnWriteArrayList<>());
        }
    }

    private FrameGraphDispatch() {
    }

    public static void register(FrameGraphAnchor anchor, FrameGraphListener listener) {
        LISTENERS.get(anchor).add(listener);
    }

    /** Registers an internal finalizer that always runs after ordinary listeners. */
    public static void registerLast(FrameGraphAnchor anchor, FrameGraphListener listener) {
        LAST_LISTENERS.get(anchor).add(listener);
    }

    /** Render thread only. Never throws. */
    public static void dispatch(FrameGraphAnchor anchor, FrameGraphBuilder builder, LevelTargetBundle targets) {
        SeamHealth.markEngaged(SeamHealth.Seam.FRAME_GRAPH_EVENTS);
        FIRED.add(anchor); // render-thread-confined, EnumSet is fine

        FrameGraphContext context = new FrameGraphContext(anchor, builder, targets);
        dispatchListeners(LISTENERS.get(anchor), context, anchor);
        dispatchListeners(LAST_LISTENERS.get(anchor), context, anchor);
    }

    private static void dispatchListeners(
            List<FrameGraphListener> listeners,
            FrameGraphContext context,
            FrameGraphAnchor anchor) {
        for (FrameGraphListener listener : listeners) {
            if (MUTED.contains(listener)) {
                continue;
            }
            try {
                listener.onFrameGraph(context);
            } catch (Throwable t) {
                MUTED.add(listener);
                MgfConstants.LOGGER.error(
                        "Frame-graph listener {} threw at {} and is muted for this session", listener, anchor, t);
            }
        }
    }

    /** Anchors observed firing so far this session (diagnostics/smoke). */
    public static Set<FrameGraphAnchor> firedAnchors() {
        return Set.copyOf(FIRED);
    }
}
