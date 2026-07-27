package dev.mgf.api;

import java.util.Set;
import java.util.Optional;

/**
 * Capability probing for the current device and for MGF's own seams.
 *
 * <p>Values are only meaningful once the graphics device exists (i.e. after
 * client startup). Before that, implementations report the most conservative
 * answer.
 */
public interface GraphicsCaps {

    /** @return the coarse tier; see {@link CapsTier} */
    CapsTier tier();

    /**
     * @param extensionName a Vulkan extension name, e.g. {@code "VK_KHR_external_memory"}
     * @return whether the extension is enabled on the live device (always
     *         {@code false} on OpenGL)
     */
    boolean hasDeviceExtension(String extensionName);

    /** @return all extensions enabled on the live device (empty on OpenGL) */
    Set<String> enabledDeviceExtensions();

    /**
     * @return whether MGF's extension-negotiation seam applied on this game
     *         version (if {@code false}, {@code requestDeviceExtension} calls
     *         were no-ops)
     */
    boolean extensionNegotiationActive();

    /**
     * @return whether MGF's frame-graph event seam has fired at least once.
     *         Note this only becomes {@code true} once a level is actually
     *         being rendered (the frame graph does not run on the title
     *         screen), and stays {@code false} if the seam failed to apply
     *         on this game version.
     */
    boolean frameGraphEventsActive();

    /**
     * @return whether MGF's shader-reload hook has run and registered custom
     *         pipelines can therefore be recompiled after resource reloads
     */
    boolean pipelineWarmupReloadActive();

    /**
     * @return whether MGF's Vulkan compute dispatcher is available on the
     *         current graphics device
     */
    boolean hasCompute();

    /**
     * @return empty when compute is available, otherwise a precise reason why
     *         the service is disabled
     */
    Optional<String> computeUnavailableReason();
}
