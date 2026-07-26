package dev.mgf.samples.interop;

import java.util.Optional;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.resources.Identifier;

import dev.mgf.api.unstable.post.PostFx;

/**
 * Visible M2 post-fx sample: a vignette overlay on the level's main target.
 * Reuses vanilla's bufferless fullscreen-triangle vertex shader
 * ({@code minecraft:core/screenquad}); the fragment shader ships in this
 * mod's assets and needs no uniforms or samplers — blending onto the target
 * makes reading the scene color unnecessary.
 */
public final class SampleVignette {

    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("mgf-sample-interop", "pipeline/vignette"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("mgf-sample-interop", "post/vignette"))
            .withColorTargetState(new ColorTargetState(
                    Optional.of(BlendFunction.TRANSLUCENT), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private SampleVignette() {
    }

    static void install() {
        PostFx.registerMainOverlay("sample_vignette", PIPELINE);
    }
}
