package dev.mgf.smoke;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.mgf.api.CapsTier;
import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.Mgf;
import dev.mgf.api.MgfRuntime;
import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphEvents;
import dev.mgf.api.unstable.pipeline.MgfPipelines;
import dev.mgf.impl.graph.FrameGraphDispatch;
import dev.mgf.impl.pipeline.PipelineWarmupRegistry;
import dev.mgf.samples.interop.SampleVignette;
import dev.mgf.samples.interop.SampleWorldGeometry;

/**
 * The assertion set. Each check appends a human-readable line; failures make
 * the whole run FAIL. Checks branch on the backend the Gradle harness said to
 * expect ({@code -Dmgf.smoke.expectedBackend}).
 */
final class SmokeChecks {

    private final List<String> lines = new ArrayList<>();
    private boolean failed;

    private SmokeChecks() {
    }

    static SmokeChecks run(String expectedBackend, long generationBeforeReload,
                           List<SmokeComputeProbe.Check> computeChecks) {
        SmokeChecks checks = new SmokeChecks();
        checks.lines.add("expectedBackend=" + expectedBackend);
        if (!Mgf.isAvailable()) {
            checks.fail("MGF runtime not available");
            return checks;
        }
        MgfRuntime runtime = Mgf.runtime();
        checks.lines.add("mgfVersion=" + runtime.version());
        checks.lines.add("validationRequested=" + Boolean.getBoolean("mgf.smoke.validation"));

        if ("vulkan".equalsIgnoreCase(expectedBackend)) {
            checks.runVulkanChecks(runtime);
        } else {
            checks.runOpenGlChecks(runtime);
        }
        for (SmokeComputeProbe.Check check : computeChecks) {
            checks.check(check.passed(), check.detail());
        }
        checks.runFrameGraphChecks(generationBeforeReload);
        return checks;
    }

    private void runFrameGraphChecks(long generationBeforeReload) {
        try {
            FrameGraphEvents.register(FrameGraphAnchor.BEFORE_EXECUTE, context -> { });
            check(true, "frameGraphListenerRegistered=true");
        } catch (Throwable t) {
            fail("frameGraphListenerRegistered threw: " + t);
        }
        // Not gated: the frame graph never runs on the title screen, so anchors
        // cannot have fired in this harness. In-world verification is manual
        // (sample-interop logs "Frame-graph anchor fired: ..." once per anchor).
        lines.add("INFO frameGraphAnchorsFired=" + FrameGraphDispatch.firedAnchors());

        // Gated on BOTH backends: compiles the sample's post pipeline through
        // the live device (GL: GLSL compile; Vulkan: shaderc -> SPIR-V), which
        // catches shader/pipeline breakage without needing a world.
        try {
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(SampleVignette.PIPELINE);
            check(compiled.isValid(), "postPipelineValid=" + compiled.isValid());
        } catch (Throwable t) {
            fail("postPipelineValid threw: " + t);
        }

        check(Mgf.runtime().caps().pipelineWarmupReloadActive(),
                "pipelineWarmupReloadActive=" + Mgf.runtime().caps().pipelineWarmupReloadActive());
        check(MgfPipelines.warmupStatus(SampleWorldGeometry.PIPELINE) == MgfPipelines.WarmupStatus.VALID,
                "worldGeometryPipelineWarmup=" + MgfPipelines.warmupStatus(SampleWorldGeometry.PIPELINE));
        check(PipelineWarmupRegistry.generation(SampleWorldGeometry.PIPELINE) > generationBeforeReload,
                "pipelineWarmupReloadGeneration="
                        + PipelineWarmupRegistry.generation(SampleWorldGeometry.PIPELINE)
                        + " (before reload " + generationBeforeReload + ")");
    }

    private void runVulkanChecks(MgfRuntime runtime) {
        check(runtime.activeBackend() == GraphicsBackendKind.VULKAN,
                "activeBackend=" + runtime.activeBackend() + " (expected VULKAN)");
        check(runtime.caps().tier() == CapsTier.VULKAN_FULL,
                "tier=" + runtime.caps().tier() + " (expected VULKAN_FULL)");
        check(runtime.caps().extensionNegotiationActive(), "negotiationActive="
                + runtime.caps().extensionNegotiationActive());
        check(runtime.caps().hasCompute(), "capsComputeAvailable=" + runtime.caps().hasCompute());
        check(runtime.caps().computeUnavailableReason().isEmpty(),
                "capsComputeUnavailableReason="
                        + runtime.caps().computeUnavailableReason().orElse("available"));
        check(runtime.caps().hasDeviceExtension(SmokeVulkanBoot.EXT_AVAILABLE),
                "capsSeesRequestedExtension=" + runtime.caps().hasDeviceExtension(SmokeVulkanBoot.EXT_AVAILABLE));

        check(SmokeVulkanBoot.callbackFired, "onDeviceCreatedFired=" + SmokeVulkanBoot.callbackFired);
        if (SmokeVulkanBoot.callbackFired) {
            check(SmokeVulkanBoot.availableEnabled,
                    "callbackSawExtensionEnabled=" + SmokeVulkanBoot.availableEnabled);
            check(SmokeVulkanBoot.missingRequired != null
                            && SmokeVulkanBoot.missingRequired.contains(SmokeVulkanBoot.EXT_NONEXISTENT),
                    "missingRequired=" + SmokeVulkanBoot.missingRequired
                            + " (expected to contain " + SmokeVulkanBoot.EXT_NONEXISTENT + ")");
            check(SmokeVulkanBoot.callbackVkDevice != 0L,
                    "callbackVkDevice=0x" + Long.toHexString(SmokeVulkanBoot.callbackVkDevice));
        }

        runtime.vkInterop().ifPresentOrElse(interop -> {
            check(interop.vkInstance() != 0L, "vkInstance=0x" + Long.toHexString(interop.vkInstance()));
            check(interop.vkDevice() != 0L, "vkDevice=0x" + Long.toHexString(interop.vkDevice()));
            check(interop.graphicsQueue() != 0L, "graphicsQueue=0x" + Long.toHexString(interop.graphicsQueue()));
            check(interop.computeQueue() != 0L, "computeQueue=0x" + Long.toHexString(interop.computeQueue()));
            check(interop.transferQueue() != 0L, "transferQueue=0x" + Long.toHexString(interop.transferQueue()));
            check(interop.vmaAllocator() != 0L, "vma=0x" + Long.toHexString(interop.vmaAllocator()));
        }, () -> fail("vkInterop empty on Vulkan backend"));
    }

    private void runOpenGlChecks(MgfRuntime runtime) {
        check(runtime.activeBackend() == GraphicsBackendKind.OPENGL,
                "activeBackend=" + runtime.activeBackend() + " (expected OPENGL)");
        check(runtime.caps().tier() == CapsTier.OPENGL_COMPAT,
                "tier=" + runtime.caps().tier() + " (expected OPENGL_COMPAT)");
        check(runtime.vkInterop().isEmpty(), "vkInteropEmpty=" + runtime.vkInterop().isEmpty());
        check(runtime.caps().enabledDeviceExtensions().isEmpty(),
                "deviceExtensionsEmpty=" + runtime.caps().enabledDeviceExtensions().isEmpty());
        check(!SmokeVulkanBoot.callbackFired,
                "onDeviceCreatedNotFired=" + !SmokeVulkanBoot.callbackFired);
        check(!runtime.caps().hasCompute(), "capsComputeUnavailable=" + !runtime.caps().hasCompute());
        String computeReason = runtime.caps().computeUnavailableReason().orElse("missing reason");
        check("Compute is unavailable on the OpenGL backend".equals(computeReason),
                "capsComputeUnavailableReason=" + computeReason);
    }

    private void check(boolean ok, String detail) {
        lines.add((ok ? "OK   " : "FAIL ") + detail);
        if (!ok) {
            failed = true;
        }
    }

    private void fail(String detail) {
        check(false, detail);
    }

    boolean passed() {
        return !failed;
    }

    List<String> lines() {
        return lines;
    }
}
