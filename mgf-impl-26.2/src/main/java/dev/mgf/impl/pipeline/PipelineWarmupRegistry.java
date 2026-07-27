package dev.mgf.impl.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

import dev.mgf.api.unstable.pipeline.MgfPipelines.WarmupStatus;
import dev.mgf.impl.core.MgfConstants;

/** Identity registry behind the unstable pipeline warm-up API. */
public final class PipelineWarmupRegistry {

    private static final Map<RenderPipeline, Entry> ENTRIES = new IdentityHashMap<>();
    private static final Map<RenderPipeline, ShaderSource> SOURCE_OWNERS = new IdentityHashMap<>();
    private static final AtomicLong WARMUP_GENERATION = new AtomicLong();

    private PipelineWarmupRegistry() {
    }

    public static void register(RenderPipeline pipeline, ShaderSource source) {
        requireCacheIsolation(pipeline);
        synchronized (ENTRIES) {
            claimSourceOwnership(pipeline, source);
            ENTRIES.put(pipeline, new Entry(pipeline, source));
        }
    }

    public static WarmupStatus status(RenderPipeline pipeline) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(pipeline);
            return entry == null ? WarmupStatus.NOT_RUN : entry.status;
        }
    }

    public static CompiledRenderPipeline precompile(RenderPipeline pipeline, ShaderSource source) {
        requireCacheIsolation(pipeline);
        synchronized (ENTRIES) {
            claimSourceOwnership(pipeline, source);
        }
        RenderSystem.assertOnRenderThread();
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, source);
        updateStatus(pipeline, compiled.isValid() ? WarmupStatus.VALID : WarmupStatus.INVALID, 0L);
        return compiled;
    }

    /** Called after vanilla has replaced its shader compilation cache. */
    public static void warmUpRegistered() {
        long generation = WARMUP_GENERATION.incrementAndGet();
        List<Entry> snapshot;
        synchronized (ENTRIES) {
            snapshot = new ArrayList<>(ENTRIES.values());
        }
        for (Entry entry : snapshot) {
            try {
                CompiledRenderPipeline compiled = precompile(entry.pipeline, entry.source);
                updateStatus(
                        entry.pipeline,
                        compiled.isValid() ? WarmupStatus.VALID : WarmupStatus.INVALID,
                        generation);
                if (!compiled.isValid()) {
                    MgfConstants.LOGGER.error("MGF pipeline warm-up failed: {}", entry.pipeline.getLocation());
                }
            } catch (Throwable t) {
                updateStatus(entry.pipeline, WarmupStatus.INVALID, generation);
                MgfConstants.LOGGER.error("MGF pipeline warm-up threw for {}", entry.pipeline.getLocation(), t);
            }
        }
    }

    /** Last shader-reload generation in which this pipeline was attempted. */
    public static long generation(RenderPipeline pipeline) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(pipeline);
            return entry == null ? 0L : entry.generation;
        }
    }

    private static void requireCacheIsolation(RenderPipeline pipeline) {
        String expected = cacheIsolationDefine(pipeline.getLocation());
        if (!pipeline.getShaderDefines().flags().contains(expected)) {
            throw new IllegalArgumentException("Custom-source pipelines must be built with MgfPipelines.builder"
                    + " so Mojang's shader cache cannot reuse a module compiled from another source");
        }
    }

    private static void claimSourceOwnership(RenderPipeline pipeline, ShaderSource source) {
        ShaderSource current = SOURCE_OWNERS.get(pipeline);
        if (current != null && current != source) {
            throw new IllegalArgumentException("A pipeline cannot be compiled from multiple ShaderSource instances");
        }
        ShaderCacheKey vertex = shaderKey(pipeline, pipeline.getVertexShader(), ShaderType.VERTEX);
        ShaderCacheKey fragment = shaderKey(pipeline, pipeline.getFragmentShader(), ShaderType.FRAGMENT);
        for (Map.Entry<RenderPipeline, ShaderSource> ownership : SOURCE_OWNERS.entrySet()) {
            RenderPipeline ownedPipeline = ownership.getKey();
            ShaderSource ownedSource = ownership.getValue();
            if (ownedPipeline == pipeline || ownedSource == source) {
                continue;
            }
            if (vertex.equals(shaderKey(ownedPipeline, ownedPipeline.getVertexShader(), ShaderType.VERTEX))
                    || fragment.equals(shaderKey(ownedPipeline, ownedPipeline.getFragmentShader(), ShaderType.FRAGMENT))) {
                throw new IllegalArgumentException("Shader cache key already belongs to a different ShaderSource;"
                        + " use a unique MgfPipelines.builder location or reuse the existing source");
            }
        }
        SOURCE_OWNERS.put(pipeline, source);
    }

    private static ShaderCacheKey shaderKey(RenderPipeline pipeline, Identifier id, ShaderType type) {
        return new ShaderCacheKey(id, type, pipeline.getShaderDefines());
    }

    /** Internal cache-key marker shared with the unstable builder facade. */
    public static String cacheIsolationDefine(Identifier location) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(location.toString().getBytes(StandardCharsets.UTF_8));
            return "MGF_PIPELINE_" + HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by the Java platform", e);
        }
    }

    private static void updateStatus(RenderPipeline pipeline, WarmupStatus status, long generation) {
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(pipeline);
            if (entry != null) {
                entry.status = status;
                if (generation != 0L) {
                    entry.generation = generation;
                }
            }
        }
    }

    private static final class Entry {

        private final RenderPipeline pipeline;
        private final ShaderSource source;
        private volatile WarmupStatus status = WarmupStatus.NOT_RUN;
        private volatile long generation;

        private Entry(RenderPipeline pipeline, ShaderSource source) {
            this.pipeline = pipeline;
            this.source = source;
        }
    }

    private record ShaderCacheKey(Identifier id, ShaderType type, ShaderDefines defines) {
    }
}
