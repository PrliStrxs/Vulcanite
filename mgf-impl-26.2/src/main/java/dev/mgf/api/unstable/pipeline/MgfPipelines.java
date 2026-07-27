package dev.mgf.api.unstable.pipeline;

import java.util.Objects;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.UniformType;

import net.minecraft.resources.Identifier;

import dev.mgf.impl.pipeline.PipelineWarmupRegistry;

/** Convenience entry points over the Minecraft 26.2 pipeline builders. */
public final class MgfPipelines {

    /** Result of the most recent warm-up attempt for a registered pipeline. */
    public enum WarmupStatus {
        NOT_RUN,
        VALID,
        INVALID
    }

    private MgfPipelines() {
    }

    /**
     * @return a vanilla builder with its diagnostic/cache location assigned
     *         and {@link PrimitiveTopology#TRIANGLES} as the overridable default;
     *         a location-derived define isolates custom shader modules from
     *         Mojang's source-agnostic backend cache
     */
    public static RenderPipeline.Builder builder(Identifier location) {
        Objects.requireNonNull(location, "location");
        return RenderPipeline.builder()
                .withLocation(location)
                .withShaderDefine(PipelineWarmupRegistry.cacheIsolationDefine(location))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
    }

    /** Builds one bind group containing uniform buffers in declaration order. */
    public static BindGroupLayout uniformLayout(String... uniformNames) {
        BindGroupLayout.Builder builder = BindGroupLayout.builder();
        for (String name : uniformNames) {
            builder.withUniform(Objects.requireNonNull(name, "uniform name"), UniformType.UNIFORM_BUFFER);
        }
        return builder.build();
    }

    /**
     * Registers a pipeline for compilation after every shader resource reload.
     * Registration is identity-based and replacing a source resets its status.
     * The pipeline must come from {@link #builder(Identifier)}.
     *
     * @return {@code pipeline}, for use in static pipeline declarations
     */
    public static RenderPipeline registerForWarmup(RenderPipeline pipeline, ShaderSource source) {
        PipelineWarmupRegistry.register(
                Objects.requireNonNull(pipeline, "pipeline"),
                Objects.requireNonNull(source, "source"));
        return pipeline;
    }

    /**
     * Compiles immediately on the render thread and records the result. The
     * pipeline must come from {@link #builder(Identifier)}.
     */
    public static CompiledRenderPipeline precompile(RenderPipeline pipeline, ShaderSource source) {
        return PipelineWarmupRegistry.precompile(
                Objects.requireNonNull(pipeline, "pipeline"),
                Objects.requireNonNull(source, "source"));
    }

    /** @return the last known warm-up state, or {@code NOT_RUN} if unregistered */
    public static WarmupStatus warmupStatus(RenderPipeline pipeline) {
        return PipelineWarmupRegistry.status(Objects.requireNonNull(pipeline, "pipeline"));
    }

}
