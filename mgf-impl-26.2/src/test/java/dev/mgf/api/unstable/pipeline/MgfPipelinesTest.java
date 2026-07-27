package dev.mgf.api.unstable.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;

import net.minecraft.resources.Identifier;

final class MgfPipelinesTest {

    @Test
    void builderAssignsPipelineLocation() {
        Identifier location = Identifier.fromNamespaceAndPath("test", "pipeline/example");

        RenderPipeline pipeline = MgfPipelines.builder(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("test", "core/example"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("test", "core/example"))
                .build();

        assertEquals(location, pipeline.getLocation());
        assertTrue(pipeline.getShaderDefines().flags().stream()
                .anyMatch(flag -> flag.startsWith("MGF_PIPELINE_")));
    }

    @Test
    void uniformLayoutPreservesDeclaredOrder() {
        BindGroupLayout layout = MgfPipelines.uniformLayout("Camera", "Material");

        assertEquals(List.of("Camera", "Material"), layout.getUniforms().stream()
                .map(BindGroupLayout.UniformDescription::name)
                .toList());
    }

    @Test
    void registeredPipelineStartsNotRun() {
        Identifier location = Identifier.fromNamespaceAndPath("test", "pipeline/warmup");
        RenderPipeline pipeline = MgfPipelines.builder(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("test", "core/warmup"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("test", "core/warmup"))
                .build();
        ShaderSource source = (id, type) -> "#version 330\nvoid main() {}";

        RenderPipeline registered = MgfPipelines.registerForWarmup(pipeline, source);

        assertSame(pipeline, registered);
        assertEquals(MgfPipelines.WarmupStatus.NOT_RUN, MgfPipelines.warmupStatus(pipeline));
    }

    @Test
    void rejectsDifferentSourcesForTheSameBackendShaderCacheKey() {
        Identifier vertex = Identifier.fromNamespaceAndPath("test", "core/cache_conflict");
        Identifier fragment = Identifier.fromNamespaceAndPath("test", "core/cache_conflict");
        RenderPipeline first = conflictingPipeline(vertex, fragment);
        RenderPipeline second = conflictingPipeline(vertex, fragment);
        ShaderSource firstSource = (id, type) -> "#version 330\nvoid main() {}";
        ShaderSource secondSource = (id, type) -> "#version 330\nvoid main() {}";

        MgfPipelines.registerForWarmup(first, firstSource);

        assertThrows(IllegalArgumentException.class,
                () -> MgfPipelines.registerForWarmup(second, secondSource));
    }

    private static RenderPipeline conflictingPipeline(Identifier vertex, Identifier fragment) {
        return MgfPipelines.builder(Identifier.fromNamespaceAndPath("test", "pipeline/cache_conflict"))
                .withVertexShader(vertex)
                .withFragmentShader(fragment)
                .build();
    }
}
