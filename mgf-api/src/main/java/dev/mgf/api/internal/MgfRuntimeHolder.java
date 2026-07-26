package dev.mgf.api.internal;

import dev.mgf.api.MgfRuntime;

/**
 * Internal bridge between the API artifact and the implementation mod.
 * Consumer mods must never call {@link #set}; use {@link dev.mgf.api.Mgf} instead.
 */
public final class MgfRuntimeHolder {

    private static volatile MgfRuntime runtime;

    private MgfRuntimeHolder() {
    }

    public static MgfRuntime get() {
        return runtime;
    }

    /** Implementation-only. Called once by the MGF bootstrap. */
    public static void set(MgfRuntime instance) {
        if (runtime != null) {
            throw new IllegalStateException("MGF runtime installed twice");
        }
        runtime = instance;
    }
}
