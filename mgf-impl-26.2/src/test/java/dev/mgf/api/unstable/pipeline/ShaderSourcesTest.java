package dev.mgf.api.unstable.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;

import net.minecraft.resources.Identifier;

final class ShaderSourcesTest {

    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("test", "core/generated");
    private static final Identifier INCLUDE = Identifier.fromNamespaceAndPath("test", "common/transform.glsl");

    @Test
    void expandsShadercIncludesAndKeepsHighestGlslVersion() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        #include <test:common/transform.glsl>
                        layout(location = 0) in vec3 Position;
                        void main() { gl_Position = transform(Position); }
                        """)
                .include(INCLUDE, """
                        #version 410
                        vec4 transform(vec3 value) { return vec4(value, 1.0); }
                        """)
                .build();

        String result = source.get(VERTEX, ShaderType.VERTEX);

        assertTrue(result.startsWith("#version 410"));
        assertTrue(result.contains("vec4 transform(vec3 value)"));
        assertTrue(result.contains("#extension GL_ARB_separate_shader_objects : require"));
        assertTrue(result.contains("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE"));
        assertTrue(!result.contains("#include"));
    }

    @Test
    void generatedShaderOverridesFallbackSource() {
        ShaderSource fallback = (id, type) -> "fallback";
        ShaderSource source = ShaderSources.builder()
                .withFallback(fallback)
                .put(VERTEX, ShaderType.VERTEX, "#version 330\nvoid main() {}")
                .build();

        assertTrue(source.get(VERTEX, ShaderType.VERTEX).contains("void main()"));
        assertEquals("fallback", source.get(
                Identifier.fromNamespaceAndPath("test", "core/fallback"), ShaderType.FRAGMENT));
    }

    @Test
    void rejectsStageInterfaceWithoutExplicitLocation() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        in vec3 Position;
                        void main() { gl_Position = vec4(Position, 1.0); }
                        """)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> source.get(VERTEX, ShaderType.VERTEX));

        assertTrue(exception.getMessage().contains("explicit layout(location"));
    }

    @Test
    void rejectsLayoutQualifierWithoutLocation() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        layout(component = 0) in vec3 Position;
                        void main() { gl_Position = vec4(Position, 1.0); }
                        """)
                .build();

        assertThrows(IllegalArgumentException.class, () -> source.get(VERTEX, ShaderType.VERTEX));
    }

    @Test
    void acceptsMultilineExplicitLocation() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        layout(location = 0)
                        in vec3 Position;
                        void main() { gl_Position = vec4(Position, 1.0); }
                        """)
                .build();

        assertDoesNotThrow(() -> source.get(VERTEX, ShaderType.VERTEX));
    }

    @Test
    void allowsBuiltinInterfaceBlocksWithoutLocation() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        out gl_PerVertex {
                            vec4 gl_Position;
                        };
                        void main() { gl_Position = vec4(0.0); }
                        """)
                .build();

        assertDoesNotThrow(() -> source.get(VERTEX, ShaderType.VERTEX));
    }

    @Test
    void doesNotTreatBuiltinArraySizeAsBuiltinInterface() {
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        out float distances[gl_MaxClipDistances];
                        void main() { gl_Position = vec4(0.0); }
                        """)
                .build();

        assertThrows(IllegalArgumentException.class, () -> source.get(VERTEX, ShaderType.VERTEX));
    }

    @Test
    void rejectsIncludeCycles() {
        Identifier first = Identifier.fromNamespaceAndPath("test", "cycle/first.glsl");
        Identifier second = Identifier.fromNamespaceAndPath("test", "cycle/second.glsl");
        ShaderSource source = ShaderSources.builder()
                .put(VERTEX, ShaderType.VERTEX, """
                        #version 330
                        #include <test:cycle/first.glsl>
                        void main() { gl_Position = vec4(0.0); }
                        """)
                .include(first, "#include <test:cycle/second.glsl>")
                .include(second, "#include <test:cycle/first.glsl>")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> source.get(VERTEX, ShaderType.VERTEX));

        assertTrue(exception.getMessage().contains("Include cycle"));
    }
}
