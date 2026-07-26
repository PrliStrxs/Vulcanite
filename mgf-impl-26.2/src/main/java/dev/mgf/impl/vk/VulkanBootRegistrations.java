package dev.mgf.impl.vk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import dev.mgf.api.vk.VulkanBootConfigurator;
import dev.mgf.api.vk.VulkanBootRegistrar;
import dev.mgf.api.vk.VulkanBootResult;
import dev.mgf.impl.core.MgfConstants;

/**
 * Collects {@code mgf:vulkan_boot} entrypoint registrations, grouped per mod.
 * Collection happens once, lazily, at first use (device-creation time), so it
 * is independent of entrypoint initialization order.
 */
public final class VulkanBootRegistrations {

    /** One extension request from one mod. */
    public record ExtensionRequest(String extension, boolean required) {
    }

    /** Everything one mod registered through its configurator. */
    public record ModRegistration(String modId,
                                  List<ExtensionRequest> requests,
                                  List<Consumer<VulkanBootResult>> deviceCreatedCallbacks) {
    }

    private static volatile List<ModRegistration> registrations;

    private VulkanBootRegistrations() {
    }

    /** @return all registrations, collecting them on first call */
    public static synchronized List<ModRegistration> collect() {
        if (registrations != null) {
            return registrations;
        }
        List<ModRegistration> collected = new ArrayList<>();
        List<EntrypointContainer<VulkanBootRegistrar>> containers = FabricLoader.getInstance()
                .getEntrypointContainers(MgfConstants.ENTRYPOINT_VULKAN_BOOT, VulkanBootRegistrar.class);

        for (EntrypointContainer<VulkanBootRegistrar> container : containers) {
            String modId = container.getProvider().getMetadata().getId();
            List<ExtensionRequest> requests = new ArrayList<>();
            List<Consumer<VulkanBootResult>> callbacks = new ArrayList<>();

            VulkanBootConfigurator configurator = new VulkanBootConfigurator() {
                @Override
                public void requestDeviceExtension(String extensionName, boolean required) {
                    if (extensionName == null || extensionName.isBlank()) {
                        MgfConstants.LOGGER.warn("Mod '{}' requested a blank Vulkan extension name; ignored", modId);
                        return;
                    }
                    requests.add(new ExtensionRequest(extensionName, required));
                }

                @Override
                public void onDeviceCreated(Consumer<VulkanBootResult> callback) {
                    if (callback != null) {
                        callbacks.add(callback);
                    }
                }
            };

            try {
                container.getEntrypoint().configureVulkan(configurator);
                collected.add(new ModRegistration(modId, List.copyOf(requests), List.copyOf(callbacks)));
            } catch (Throwable t) {
                MgfConstants.LOGGER.error("Vulkan boot registrar of mod '{}' threw; its requests are skipped", modId, t);
            }
        }
        registrations = List.copyOf(collected);
        return registrations;
    }
}
