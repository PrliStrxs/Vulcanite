package dev.mgf.impl.vk;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;

import dev.mgf.api.vk.VulkanBootResult;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.core.SeamHealth;
import dev.mgf.impl.vk.VulkanBootRegistrations.ExtensionRequest;
import dev.mgf.impl.vk.VulkanBootRegistrations.ModRegistration;

/**
 * Device-creation-time negotiation and the post-creation callback dispatch.
 * Both entry points are invoked from mixins and must never throw upward.
 */
public final class VulkanBootNegotiation {

    /**
     * Outcome of negotiation, readable after device creation.
     *
     * @param requestedExtensions each consumer-requested extension and whether it was enabled
     * @param vkPhysicalDevice raw {@code VkPhysicalDevice} handle (vanilla exposes no accessor)
     * @param deviceExtensions the exact extension list the device was created with —
     *        the authoritative clean source ({@code DeviceInfo.underlyingExtensions}
     *        is a debug list with {@code " (I)"}/{@code " (D)"} suffixes)
     * @param missingRequiredByMod per mod id, its required-but-unavailable extensions
     */
    public record Outcome(Map<String, Boolean> requestedExtensions,
                          long vkPhysicalDevice,
                          Set<String> deviceExtensions,
                          Map<String, Set<String>> missingRequiredByMod) {
    }

    private static volatile Outcome outcome;
    private static final AtomicBoolean DEVICE_CREATED_FIRED = new AtomicBoolean();

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

        Map<String, Boolean> requested = new HashMap<>();
        Map<String, Set<String>> missingRequiredByMod = new HashMap<>();

        for (ModRegistration registration : VulkanBootRegistrations.collect()) {
            for (ExtensionRequest request : registration.requests()) {
                boolean enabled = applyExtensionRequest(registration.modId(), request,
                        vanillaExtensions, physicalDevice);
                requested.merge(request.extension(), enabled, Boolean::logicalOr);
                if (!enabled && request.required()) {
                    missingRequiredByMod
                            .computeIfAbsent(registration.modId(), id -> new HashSet<>())
                            .add(request.extension());
                }
            }
        }

        outcome = new Outcome(Map.copyOf(requested), physicalDevice.vkPhysicalDevice().address(),
                Set.copyOf(vanillaExtensions), deepCopy(missingRequiredByMod));
        MgfConstants.LOGGER.info("Vulkan boot negotiation done: {} extension(s) requested, device extension list = {}",
                requested.size(), vanillaExtensions);
    }

    /**
     * Invoked from {@code VulkanDeviceMixin} at the tail of the
     * {@code VulkanDevice} constructor: the earliest point where queues, VMA,
     * and the command encoder are live. Fires each mod's
     * {@code onDeviceCreated} callbacks exactly once, isolated from each other.
     */
    public static void fireDeviceCreated(VulkanDevice device) {
        if (!DEVICE_CREATED_FIRED.compareAndSet(false, true)) {
            return;
        }
        SeamHealth.markEngaged(SeamHealth.Seam.DEVICE_CREATED_HOOK);

        Outcome negotiated = outcome;
        Set<String> enabled = negotiated != null
                ? negotiated.deviceExtensions()
                : VulkanDeviceAccess.enabledDeviceExtensions(device);
        VkInteropImpl interop = new VkInteropImpl(device);

        for (ModRegistration registration : VulkanBootRegistrations.collect()) {
            if (registration.deviceCreatedCallbacks().isEmpty()) {
                continue;
            }
            Set<String> missingRequired = negotiated != null
                    ? negotiated.missingRequiredByMod().getOrDefault(registration.modId(), Set.of())
                    : Set.of();
            VulkanBootResult result = new VulkanBootResultImpl(interop, enabled, missingRequired);
            for (Consumer<VulkanBootResult> callback : registration.deviceCreatedCallbacks()) {
                try {
                    callback.accept(result);
                } catch (Throwable t) {
                    MgfConstants.LOGGER.error("onDeviceCreated callback of mod '{}' threw",
                            registration.modId(), t);
                }
            }
        }
    }

    private static boolean applyExtensionRequest(String modId,
                                                 ExtensionRequest request,
                                                 Collection<String> extensions,
                                                 VulkanPhysicalDevice physicalDevice) {
        if (extensions.contains(request.extension())) {
            MgfConstants.LOGGER.debug("Mod '{}': extension {} already enabled", modId, request.extension());
            return true;
        }
        if (physicalDevice.hasDeviceExtension(request.extension())) {
            extensions.add(request.extension());
            MgfConstants.LOGGER.info("Mod '{}': enabling Vulkan device extension {}", modId, request.extension());
            return true;
        }
        if (request.required()) {
            MgfConstants.LOGGER.warn(
                    "Mod '{}' requires Vulkan device extension {} but this GPU does not support it; the mod should disable itself",
                    modId, request.extension());
        } else {
            MgfConstants.LOGGER.info("Mod '{}': optional Vulkan device extension {} not supported by this GPU",
                    modId, request.extension());
        }
        return false;
    }

    private static Map<String, Set<String>> deepCopy(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((mod, extensions) -> copy.put(mod, Set.copyOf(extensions)));
        return Map.copyOf(copy);
    }
}
