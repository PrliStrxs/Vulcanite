package dev.mgf.api;

import java.util.Optional;

import dev.mgf.api.provider.ProviderSelections;
import dev.mgf.api.vk.VkInterop;

/**
 * Live view of the MGF runtime state.
 *
 * <p>All methods are safe to call from the render thread. The active backend is
 * always detected at runtime — never from configuration — because vanilla's
 * automatic fallback can silently switch backends between launches.
 */
public interface MgfRuntime {

    /** @return the graphics backend the game is actually running on */
    GraphicsBackendKind activeBackend();

    /** @return capability information for the current device and MGF seams */
    GraphicsCaps caps();

    /**
     * Vulkan native interop services.
     *
     * @return present only when the game runs on the Vulkan backend and the
     *         interop seam initialized successfully
     */
    Optional<VkInterop> vkInterop();

    /** @return immutable provider registration, selection, and session diagnostics */
    ProviderSelections providers();

    /** @return the MGF implementation version string */
    String version();
}
