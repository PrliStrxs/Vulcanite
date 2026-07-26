package dev.mgf.impl.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether each fragile vanilla seam actually engaged at runtime.
 *
 * <p>MGF's mixins are declared non-required ({@code defaultRequire: 0}): on a
 * game version where a target moved, the game must keep booting — a crash
 * during startup would trip vanilla's watchdog and silently force the player
 * back to OpenGL. Each seam therefore reports here when it fires, and
 * everything downstream degrades based on these flags instead of assuming.
 */
public final class SeamHealth {

    /** Fragile seams, one per mixin-backed integration point. */
    public enum Seam {
        /** {@code VulkanBackendMixin} fired during Vulkan device creation. */
        EXTENSION_NEGOTIATION,
        /** {@code VulkanDeviceMixin} fired after the Vulkan device was constructed. */
        DEVICE_CREATED_HOOK
    }

    private static final Set<Seam> ENGAGED = ConcurrentHashMap.newKeySet();

    private SeamHealth() {
    }

    public static void markEngaged(Seam seam) {
        if (ENGAGED.add(seam)) {
            MgfConstants.LOGGER.info("MGF seam engaged: {}", seam);
        }
    }

    public static boolean isEngaged(Seam seam) {
        return ENGAGED.contains(seam);
    }
}
