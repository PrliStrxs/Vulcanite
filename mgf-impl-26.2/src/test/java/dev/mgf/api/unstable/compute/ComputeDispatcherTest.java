package dev.mgf.api.unstable.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ComputeDispatcherTest {

    @Test
    void programDescriptorValidatesRequiredFields() {
        assertThrows(NullPointerException.class,
                () -> new ComputeDispatcher.ProgramDescriptor(null, "void main() {}", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.ProgramDescriptor(" ", "void main() {}", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.ProgramDescriptor("program", " ", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.ProgramDescriptor("program", "void main() {}", 0));
    }

    @Test
    void bufferDescriptorValidatesRequiredFields() {
        assertThrows(NullPointerException.class,
                () -> new ComputeDispatcher.BufferDescriptor(null, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.BufferDescriptor(" ", 4));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.BufferDescriptor("buffer", 3));
    }

    @Test
    void dispatchSnapshotsBindingsAndValidatesCounts() {
        TestProgram program = new TestProgram("program", 1);
        TestBuffer buffer = new TestBuffer("buffer", 16);
        List<ComputeDispatcher.Buffer> bindings = new ArrayList<>(List.of(buffer));

        ComputeDispatcher.Dispatch dispatch = new ComputeDispatcher.Dispatch(
                program, bindings, 2, 3, 4);
        bindings.clear();

        assertEquals(List.of(buffer), dispatch.storageBuffers());
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.Dispatch(program, List.of(), 1, 1, 1));
    }

    @Test
    void dispatchRejectsNullBindingsAndNonPositiveGroups() {
        TestProgram program = new TestProgram("program", 1);
        TestBuffer buffer = new TestBuffer("buffer", 16);

        assertThrows(NullPointerException.class,
                () -> new ComputeDispatcher.Dispatch(program, null, 1, 1, 1));
        assertThrows(NullPointerException.class,
                () -> new ComputeDispatcher.Dispatch(program, java.util.Arrays.asList((ComputeDispatcher.Buffer) null),
                        1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.Dispatch(program, List.of(buffer), 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.Dispatch(program, List.of(buffer), 1, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ComputeDispatcher.Dispatch(program, List.of(buffer), 1, 1, 0));
    }

    @Test
    void autoExposureRegistrationValidatesNamesAndDuplicates() {
        assertThrows(IllegalArgumentException.class,
                () -> ComputeEffects.registerMainColorAutoExposure(" "));

        String name = "compute-test-duplicate";
        ComputeEffects.registerMainColorAutoExposure(name);

        assertThrows(IllegalStateException.class,
                () -> ComputeEffects.registerMainColorAutoExposure(name));
    }

    private record TestProgram(String label, int storageBufferBindings)
            implements ComputeDispatcher.Program {

        @Override
        public void close() {
        }
    }

    private record TestBuffer(String label, int size) implements ComputeDispatcher.Buffer {

        @Override
        public void write(ByteBuffer source) {
        }

        @Override
        public ByteBuffer read() {
            return ByteBuffer.allocateDirect(size);
        }

        @Override
        public void close() {
        }
    }
}
