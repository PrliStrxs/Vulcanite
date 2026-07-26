package dev.mgf.samples.interop;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;

import dev.mgf.api.GraphicsBackendKind;
import dev.mgf.api.Mgf;
import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphEvents;

/**
 * M2 async depth readback sample: ~2 seconds into world rendering, copies the
 * main target's D32_FLOAT depth texture into a mappable buffer via a
 * frame-graph pass and logs the center-pixel depth when the GPU-completion
 * callback fires (1-2 frames later, on the render thread — vanilla's
 * screenshot pattern). Reverse-Z: sky reads 0.0, near geometry approaches 1.
 *
 * <p>Vulkan-only: the GL backend's {@code copyTextureToBuffer} attaches the
 * source as a color attachment unconditionally, so depth sources cannot work
 * there (vanilla limitation, bytecode-verified).
 */
final class SampleDepthReadback {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");
    private static final int TRIGGER_FRAME = 120;

    private static final AtomicInteger FRAMES = new AtomicInteger();
    private static final AtomicBoolean DONE = new AtomicBoolean();

    private SampleDepthReadback() {
    }

    static void install() {
        FrameGraphEvents.register(FrameGraphAnchor.BEFORE_EXECUTE, context -> {
            if (DONE.get() || FRAMES.incrementAndGet() != TRIGGER_FRAME || !DONE.compareAndSet(false, true)) {
                return;
            }
            if (Mgf.runtime().activeBackend() != GraphicsBackendKind.VULKAN) {
                LOGGER.info("Depth readback skipped: the OpenGL backend cannot copy depth textures to buffers (vanilla limitation)");
                return;
            }
            ResourceHandle<RenderTarget> main = context.targets().main;
            if (main == null) {
                return;
            }
            FramePass pass = context.builder().addPass("mgf_sample_depth_readback");
            pass.reads(main);
            pass.disableCulling(); // read-only pass would otherwise be culled
            pass.executes(() -> copyDepth(main.get()));
        });
    }

    private static void copyDepth(RenderTarget target) {
        GpuTexture depth = target.getDepthTexture();
        int width = target.width;
        int height = target.height;
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(() -> "MGF sample depth readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * 4);

        device.createCommandEncoder().copyTextureToBuffer(depth, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
                float centerDepth = view.data().getFloat(((height / 2) * width + width / 2) * 4);
                LOGGER.info("Async depth readback complete: center depth = {} ({}x{}, D32_FLOAT, reverse-Z)",
                        centerDepth, width, height);
            } finally {
                buffer.close();
            }
        }, 0);
    }
}
