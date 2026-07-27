package dev.mgf.api.unstable.compute;

import java.util.Optional;

import dev.mgf.impl.compute.ComputeServiceRegistry;

/** Entry point for MGF's per-device compute service. */
public final class ComputeServices {

    private ComputeServices() {
    }

    /** @return the live Vulkan dispatcher, or empty on unsupported backends */
    public static Optional<ComputeDispatcher> current() {
        return ComputeServiceRegistry.current();
    }

    /** @return empty when {@link #current()} is present */
    public static Optional<String> unavailableReason() {
        return ComputeServiceRegistry.unavailableReason();
    }
}
