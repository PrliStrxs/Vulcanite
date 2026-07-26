package dev.mgf.api.unstable.graph;

/**
 * Callback invoked on the render thread while vanilla assembles the frame
 * graph. Listeners may add passes via {@link FrameGraphContext#builder()};
 * they must be fast, must not touch the graphics device outside pass
 * {@code executes} closures, and must not throw (exceptions are caught,
 * logged once, and the listener is muted for the session).
 */
@FunctionalInterface
public interface FrameGraphListener {

    void onFrameGraph(FrameGraphContext context);
}
