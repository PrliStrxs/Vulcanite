package dev.mgf.api;

import dev.mgf.api.internal.MgfRuntimeHolder;

/**
 * Static entry point of the MGF API.
 *
 * <p>The runtime becomes available once the MGF implementation mod has bootstrapped,
 * which happens before the graphics device is created. Consumer mods should not
 * cache the returned instance across resource reloads; querying it is cheap.
 */
public final class Mgf {

    private Mgf() {
    }

    /**
     * @return the active MGF runtime
     * @throws IllegalStateException if the MGF implementation mod is not loaded
     *         or has not bootstrapped yet
     */
    public static MgfRuntime runtime() {
        MgfRuntime runtime = MgfRuntimeHolder.get();
        if (runtime == null) {
            throw new IllegalStateException(
                    "MGF runtime is not available. Is the 'mgf' mod installed, and are you querying after client init?");
        }
        return runtime;
    }

    /** @return {@code true} once {@link #runtime()} can be called safely. */
    public static boolean isAvailable() {
        return MgfRuntimeHolder.get() != null;
    }
}
