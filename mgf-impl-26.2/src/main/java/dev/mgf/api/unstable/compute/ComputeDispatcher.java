package dev.mgf.api.unstable.compute;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

/**
 * Vulkan compute execution over MGF-owned storage buffers.
 *
 * <p><b>Unstable surface:</b> this API ships in the per-drop implementation
 * artifact while its synchronization and lifetime contract is hardened. It
 * deliberately exposes no Mojang, LWJGL, or Vulkan types.
 *
 * <p>All methods must be called on the render thread. Dispatches are recorded
 * on vanilla's graphics queue. Call {@link #submitAndWait()} before reading a
 * result or closing resources used by recorded work.
 */
public interface ComputeDispatcher {

    Program createProgram(ProgramDescriptor descriptor);

    Buffer createBuffer(BufferDescriptor descriptor);

    /** Records one compute dispatch. Every listed buffer is bound read-write. */
    void dispatch(Dispatch command);

    /** Submits all recorded work and waits until its results are host-visible. */
    void submitAndWait();

    interface Program extends AutoCloseable {
        String label();

        int storageBufferBindings();

        @Override
        void close();
    }

    interface Buffer extends AutoCloseable {
        String label();

        int size();

        /** Replaces bytes from offset zero; remaining bytes are unchanged. */
        void write(ByteBuffer source);

        /** Returns a new native-order direct buffer containing all bytes. */
        ByteBuffer read();

        @Override
        void close();
    }

    record ProgramDescriptor(String label, String glslSource, int storageBufferBindings) {
        public ProgramDescriptor {
            label = requireText(label, "label");
            glslSource = requireText(glslSource, "glslSource");
            if (storageBufferBindings < 1) {
                throw new IllegalArgumentException("storageBufferBindings must be at least one");
            }
        }
    }

    record BufferDescriptor(String label, int size) {
        public BufferDescriptor {
            label = requireText(label, "label");
            if (size < 4) {
                throw new IllegalArgumentException("size must be at least four bytes");
            }
        }
    }

    record Dispatch(Program program, List<Buffer> storageBuffers,
                    int groupCountX, int groupCountY, int groupCountZ) {
        public Dispatch {
            program = Objects.requireNonNull(program, "program");
            storageBuffers = List.copyOf(storageBuffers);
            if (storageBuffers.size() != program.storageBufferBindings()) {
                throw new IllegalArgumentException("Expected " + program.storageBufferBindings()
                        + " storage buffers, got " + storageBuffers.size());
            }
            if (storageBuffers.stream().anyMatch(Objects::isNull)) {
                throw new NullPointerException("storageBuffers contains null");
            }
            if (groupCountX < 1 || groupCountY < 1 || groupCountZ < 1) {
                throw new IllegalArgumentException("group counts must all be positive");
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
