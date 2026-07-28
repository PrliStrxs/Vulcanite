package dev.mgf.impl.core;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.GraphicsCaps;
import dev.mgf.api.MgfRuntime;
import dev.mgf.api.provider.ProviderSelections;
import dev.mgf.api.vk.VkInterop;
import dev.mgf.impl.provider.ProviderRuntime;
import dev.mgf.impl.vk.VkInteropImpl;
import dev.mgf.impl.vk.VulkanDeviceAccess;

/** Live {@link MgfRuntime}. Stateless: every query reflects the current device. */
public final class MgfRuntimeImpl implements MgfRuntime {

    private final ProviderRuntime providerRuntime;
    private final GraphicsCaps caps = new GraphicsCapsImpl();
    private final String version = FabricLoader.getInstance()
            .getModContainer(MgfConstants.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    public MgfRuntimeImpl(ProviderRuntime providerRuntime) {
        this.providerRuntime = providerRuntime;
    }

    @Override
    public GraphicsBackendKind activeBackend() {
        if (VulkanDeviceAccess.current().isPresent()) {
            return GraphicsBackendKind.VULKAN;
        }
        return VulkanDeviceAccess.gpuDevice().isPresent()
                ? GraphicsBackendKind.OPENGL
                : GraphicsBackendKind.UNKNOWN;
    }

    @Override
    public GraphicsCaps caps() {
        return caps;
    }

    @Override
    public Optional<VkInterop> vkInterop() {
        return VulkanDeviceAccess.current().map(VkInteropImpl::new);
    }

    @Override
    public ProviderSelections providers() {
        return providerRuntime.selections();
    }

    @Override
    public String version() {
        return version;
    }
}
