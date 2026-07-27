package dev.mgf.samples.interop;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import dev.mgf.api.graph.FrameGraphAnchor;
import dev.mgf.api.unstable.graph.FrameGraphEvents;
import dev.mgf.api.unstable.pipeline.MgfPipelines;
import dev.mgf.api.unstable.pipeline.ShaderSources;

/**
 * M3 acceptance sample: a rotating, depth-tested pyramid rendered four blocks
 * in front of the camera through a custom vertex buffer, UBO, and shaders.
 * The vertex shader/include come from the resource pack; the fragment shader
 * is generated at runtime and compiled through the same source chain.
 */
public final class SampleWorldGeometry {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Sample-Interop");
    private static final Identifier PIPELINE_ID = id("pipeline/world_geometry");
    private static final Identifier VERTEX_SHADER = id("core/world_geometry");
    private static final Identifier FRAGMENT_SHADER = id("core/world_geometry_generated");
    private static final int VERTEX_COUNT = 18;
    private static final int UNIFORM_SIZE = 80;
    private static final float DISTANCE = 4.0F;

    private static final String GENERATED_FRAGMENT_SHADER = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require

            #include <mgf-sample-interop:sample/world_geometry.glsl>

            layout(location = 0) in vec4 vertexColor;
            layout(location = 0) out vec4 fragColor;

            void main() {
                fragColor = vertexColor * Tint;
            }
            """;

    public static final ShaderSource SHADER_SOURCE = ShaderSources.builder()
            .put(FRAGMENT_SHADER, ShaderType.FRAGMENT, GENERATED_FRAGMENT_SHADER)
            .withResourcePackFallback()
            .build();

    public static final RenderPipeline PIPELINE = MgfPipelines.registerForWarmup(
            MgfPipelines.builder(PIPELINE_ID)
                    .withVertexShader(VERTEX_SHADER)
                    .withFragmentShader(FRAGMENT_SHADER)
                    .withBindGroupLayout(MgfPipelines.uniformLayout("MgfSample"))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withCull(false)
                    .build(),
            SHADER_SOURCE);

    private static GpuBuffer vertexBuffer;
    private static GpuBuffer uniformBuffer;

    private SampleWorldGeometry() {
    }

    static void install() {
        FrameGraphEvents.register(FrameGraphAnchor.BEFORE_EXECUTE, context -> {
            ResourceHandle<RenderTarget> main = context.targets().main;
            if (main == null) {
                return;
            }
            FramePass pass = context.builder().addPass("mgf_sample_world_geometry");
            ResourceHandle<RenderTarget> output = pass.readsAndWrites(main);
            context.targets().main = output;
            pass.executes(() -> draw(output.get()));
        });
        LOGGER.info("M3 world-geometry sample registered");
    }

    public static void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (uniformBuffer != null) {
            uniformBuffer.close();
            uniformBuffer = null;
        }
    }

    private static void draw(RenderTarget target) {
        ensureBuffers();
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        float seconds = (System.nanoTime() % 20_000_000_000L) / 1_000_000_000.0F;
        Matrix4f mvp = camera.getViewRotationProjectionMatrix(new Matrix4f())
                .translate(
                        forward.x() * DISTANCE + up.x() * 0.15F,
                        forward.y() * DISTANCE + up.y() * 0.15F,
                        forward.z() * DISTANCE + up.z() * 0.15F)
                .rotateY(seconds * 0.8F)
                .rotateX(0.18F);
        float pulse = 0.82F + 0.18F * (float) Math.sin(seconds * 2.0F);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UNIFORM_SIZE)
                    .putMat4f(mvp)
                    .putVec4(pulse, 1.0F, 1.0F, 1.0F)
                    .get();
            encoder.writeToBuffer(uniformBuffer.slice(), data);
        }

        try (RenderPass pass = encoder.createRenderPass(
                () -> "MGF sample world geometry",
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, vertexBuffer.slice());
            pass.setUniform("MgfSample", uniformBuffer);
            pass.draw(VERTEX_COUNT, 1, 0, 0);
        }
    }

    private static void ensureBuffers() {
        if (vertexBuffer != null && uniformBuffer != null) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        vertexBuffer = createVertexBuffer(device);
        uniformBuffer = device.createBuffer(
                () -> "MGF sample world geometry uniform",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                UNIFORM_SIZE);
    }

    private static GpuBuffer createVertexBuffer(GpuDevice device) {
        int capacity = VERTEX_COUNT * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(capacity)) {
            BufferBuilder vertices = new BufferBuilder(
                    bytes,
                    PIPELINE.getPrimitiveTopology(),
                    DefaultVertexFormat.POSITION_COLOR);

            triangle(vertices, 0.0F, 0.85F, 0.0F, -0.75F, -0.65F, 0.75F, 0.75F, -0.65F, 0.75F,
                    255, 80, 90);
            triangle(vertices, 0.0F, 0.85F, 0.0F, 0.75F, -0.65F, 0.75F, 0.75F, -0.65F, -0.75F,
                    255, 205, 65);
            triangle(vertices, 0.0F, 0.85F, 0.0F, 0.75F, -0.65F, -0.75F, -0.75F, -0.65F, -0.75F,
                    70, 220, 175);
            triangle(vertices, 0.0F, 0.85F, 0.0F, -0.75F, -0.65F, -0.75F, -0.75F, -0.65F, 0.75F,
                    90, 145, 255);
            triangle(vertices, -0.75F, -0.65F, 0.75F, -0.75F, -0.65F, -0.75F, 0.75F, -0.65F, -0.75F,
                    165, 95, 235);
            triangle(vertices, -0.75F, -0.65F, 0.75F, 0.75F, -0.65F, -0.75F, 0.75F, -0.65F, 0.75F,
                    165, 95, 235);

            try (MeshData mesh = vertices.buildOrThrow()) {
                return device.createBuffer(
                        () -> "MGF sample world geometry vertices",
                        GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            }
        }
    }

    private static void triangle(
            BufferBuilder vertices,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            int red, int green, int blue) {
        vertices.addVertex(ax, ay, az).setColor(red, green, blue, 255);
        vertices.addVertex(bx, by, bz).setColor(red, green, blue, 255);
        vertices.addVertex(cx, cy, cz).setColor(red, green, blue, 255);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mgf-sample-interop", path);
    }
}
