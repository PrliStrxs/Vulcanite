package dev.mgf.impl.compute;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vulkan.VulkanDevice;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphContext;
import dev.mgf.impl.core.MgfConstants;
import dev.mgf.impl.graph.FrameGraphDispatch;

/** Registration, frame-graph ordering, and per-device auto-exposure lifetime. */
public final class ComputeAutoExposureRegistry {

    private static final List<String> EFFECTS = new CopyOnWriteArrayList<>();
    private static final Map<String, VulkanAutoExposure> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> EXECUTED = ConcurrentHashMap.newKeySet();
    private static volatile boolean bootstrapped;
    private static volatile boolean unavailableLogged;

    private ComputeAutoExposureRegistry() {
    }

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        FrameGraphDispatch.registerLast(FrameGraphAnchor.BEFORE_EXECUTE,
                ComputeAutoExposureRegistry::addPasses);
    }

    public static void register(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (EFFECTS.contains(name)) {
            throw new IllegalStateException("Auto-exposure effect already registered: " + name);
        }
        EFFECTS.add(name);
    }

    public static synchronized void onDeviceClosing(VulkanDevice device) {
        try {
            for (Map.Entry<String, VulkanAutoExposure> entry : ACTIVE.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (Throwable t) {
                    MgfConstants.LOGGER.error(
                            "Failed to close compute auto exposure '{}'; continuing", entry.getKey(), t);
                }
            }
        } finally {
            ACTIVE.clear();
            FAILED.clear();
            EXECUTED.clear();
            unavailableLogged = false;
        }
    }

    private static void addPasses(FrameGraphContext context) {
        if (EFFECTS.isEmpty() || context.targets().main == null) {
            return;
        }
        if (ComputeServiceRegistry.vulkanDispatcher().isEmpty()) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                MgfConstants.LOGGER.info("Compute auto exposure disabled: {}",
                        ComputeServiceRegistry.unavailableReason().orElse("unknown reason"));
            }
            return;
        }
        for (String name : EFFECTS) {
            if (FAILED.contains(name)) {
                continue;
            }
            FramePass pass = context.builder().addPass("mgf_compute_" + sanitize(name));
            ResourceHandle<RenderTarget> output = pass.readsAndWrites(context.targets().main);
            context.targets().main = output;
            pass.executes(() -> execute(name, output.get()));
        }
    }

    private static void execute(String name, RenderTarget target) {
        try {
            VulkanComputeDispatcher dispatcher = ComputeServiceRegistry.vulkanDispatcher()
                    .orElseThrow(() -> new IllegalStateException("Vulkan compute dispatcher disappeared"));
            VulkanAutoExposure effect = ACTIVE.computeIfAbsent(name,
                    ignored -> {
                        MgfConstants.LOGGER.info("Creating compute auto exposure '{}'", name);
                        return new VulkanAutoExposure(dispatcher);
                    });
            effect.execute(target);
            if (EXECUTED.add(name)) {
                MgfConstants.LOGGER.info("Compute auto exposure '{}' recorded its first frame", name);
            }
        } catch (Throwable t) {
            VulkanAutoExposure effect = ACTIVE.remove(name);
            if (effect != null) {
                effect.close();
            }
            FAILED.add(name);
            MgfConstants.LOGGER.error("Compute auto exposure '{}' failed and was disabled", name, t);
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
