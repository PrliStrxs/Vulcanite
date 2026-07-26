package dev.mgf.impl.vk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import dev.mgf.api.vk.VulkanBootConfigurator;
import dev.mgf.api.vk.VulkanBootRegistrar;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.SeamHealth;

/**
 * Collects {@code mgf:vulkan_boot} entrypoint requests and merges them into
 * vanilla's device-creation arguments. Invoked from {@code VulkanBackendMixin}
 * on the render thread, immediately before {@code vkCreateDevice}.
 */
public final class VulkanBootNegotiation {

    /** One consumer request, kept for logging and post-boot inspection. */
    private record ExtensionRequest(String modId, String extension, boolean required) {
    }

    /** Outcome of negotiation, readable after device creation (e.g. by diagnostics). */
    public record Outcome(Map<String, Boolean> requestedExtensions, long vkPhysicalDevice) {
    }

    private static volatile Outcome outcome;

    private VulkanBootNegotiation() {
    }

    /** @return the negotiation outcome, or {@code null} before Vulkan device creation */
    public static Outcome outcome() {
        return outcome;
    }

    /**
     * Applies all consumer requests by mutating vanilla's extension collection
     * <b>in place</b>. Bytecode-verified on 26.2: the same local {@code HashSet}
     * is passed both to the private {@code createDevice} call and to the
     * {@code VulkanDevice} constructor (which feeds
     * {@code DeviceInfo.underlyingExtensions}), so in-place mutation keeps
     * vanilla's bookkeeping accurate — replacing the argument would enable the
     * extensions on the device but leave them missing from {@code DeviceInfo}.
     *
     * <p>The feature set is currently untouched (the stable API only exposes
     * extension requests); vanilla passes a mutable copy there too, so future
     * feature support can use the same in-place approach.
     */
    public static void negotiate(Collection<String> vanillaExtensions,
                                 Set<VulkanFeature> vanillaFeatures,
                                 VulkanPhysicalDevice physicalDevice) {
        SeamHealth.markEngaged(SeamHealth.Seam.EXTENSION_NEGOTIATION);

        Map<String, Boolean> requested = new ConcurrentHashMap<>();
        for (ExtensionRequest request : collectRequests()) {
            boolean enabled = applyExtensionRequest(request, vanillaExtensions, physicalDevice);
            requested.merge(request.extension(), enabled, Boolean::logicalOr);
        }

        outcome = new Outcome(Map.copyOf(requested), physicalDevice.vkPhysicalDevice().address());
        MgfConstants.LOGGER.info("Vulkan boot negotiation done: {} extension(s) requested, device extension list = {}",
                requested.size(), vanillaExtensions);
    }

    private static List<ExtensionRequest> collectRequests() {
        List<ExtensionRequest> requests = new ArrayList<>();
        List<EntrypointContainer<VulkanBootRegistrar>> containers = FabricLoader.getInstance()
                .getEntrypointContainers(MgfConstants.ENTRYPOINT_VULKAN_BOOT, VulkanBootRegistrar.class);

        for (EntrypointContainer<VulkanBootRegistrar> container : containers) {
            String modId = container.getProvider().getMetadata().getId();
            VulkanBootConfigurator configurator = (extensionName, required) -> {
                if (extensionName == null || extensionName.isBlank()) {
                    MgfConstants.LOGGER.warn("Mod '{}' requested a blank Vulkan extension name; ignored", modId);
                    return;
                }
                requests.add(new ExtensionRequest(modId, extensionName, required));
            };
            try {
                container.getEntrypoint().configureVulkan(configurator);
            } catch (Throwable t) {
                MgfConstants.LOGGER.error("Vulkan boot registrar of mod '{}' threw; its requests are skipped", modId, t);
            }
        }
        return requests;
    }

    private static boolean applyExtensionRequest(ExtensionRequest request,
                                                 Collection<String> extensions,
                                                 VulkanPhysicalDevice physicalDevice) {
        if (extensions.contains(request.extension())) {
            MgfConstants.LOGGER.debug("Mod '{}': extension {} already enabled", request.modId(), request.extension());
            return true;
        }
        if (physicalDevice.hasDeviceExtension(request.extension())) {
            extensions.add(request.extension());
            MgfConstants.LOGGER.info("Mod '{}': enabling Vulkan device extension {}", request.modId(), request.extension());
            return true;
        }
        if (request.required()) {
            MgfConstants.LOGGER.warn(
                    "Mod '{}' requires Vulkan device extension {} but this GPU does not support it; the mod should disable itself",
                    request.modId(), request.extension());
        } else {
            MgfConstants.LOGGER.info("Mod '{}': optional Vulkan device extension {} not supported by this GPU",
                    request.modId(), request.extension());
        }
        return false;
    }
}
