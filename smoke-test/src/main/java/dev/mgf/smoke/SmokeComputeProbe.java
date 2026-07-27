package dev.mgf.smoke;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import dev.mgf.api.unstable.compute.ComputeDispatcher;
import dev.mgf.api.unstable.compute.ComputeServices;

/** Deterministic Vulkan compute readback kept alive across a resource reload. */
final class SmokeComputeProbe implements AutoCloseable {

    private static final int WORDS = 256;
    private static final int GROUPS = WORDS / 64;
    private static final int STRESS_ITERATIONS = 64;

    private static final String INITIALIZE_SHADER = """
            #version 450
            layout(local_size_x = 64) in;
            layout(std430, binding = 0) buffer Data { uint values[]; };
            void main() {
                uint index = gl_GlobalInvocationID.x;
                if (index < 256u) {
                    values[index] = index * 3u + 7u;
                }
            }
            """;

    private static final String STRESS_SHADER = """
            #version 450
            layout(local_size_x = 64) in;
            layout(std430, binding = 0) buffer Data { uint values[]; };
            void main() {
                uint index = gl_GlobalInvocationID.x;
                if (index < 256u) {
                    values[index] = values[index] * 1664525u + 1013904223u;
                }
            }
            """;

    record Check(boolean passed, String detail) {
    }

    private final List<Check> checks = new ArrayList<>();
    private final ComputeDispatcher dispatcher;
    private final ComputeDispatcher.Program initializeProgram;
    private final ComputeDispatcher.Program stressProgram;
    private final ComputeDispatcher.Buffer buffer;

    private SmokeComputeProbe(ComputeDispatcher dispatcher,
                              ComputeDispatcher.Program initializeProgram,
                              ComputeDispatcher.Program stressProgram,
                              ComputeDispatcher.Buffer buffer) {
        this.dispatcher = dispatcher;
        this.initializeProgram = initializeProgram;
        this.stressProgram = stressProgram;
        this.buffer = buffer;
    }

    static SmokeComputeProbe prepare(String expectedBackend) {
        if (!"vulkan".equalsIgnoreCase(expectedBackend)) {
            SmokeComputeProbe probe = new SmokeComputeProbe(null, null, null, null);
            probe.checks.add(new Check(ComputeServices.current().isEmpty(),
                    "computeUnavailableOnOpenGl=" + ComputeServices.current().isEmpty()));
            String reason = ComputeServices.unavailableReason().orElse("missing reason");
            probe.checks.add(new Check("Compute is unavailable on the OpenGL backend".equals(reason),
                    "computeUnavailableReason=" + reason));
            return probe;
        }

        ComputeDispatcher dispatcher = ComputeServices.current()
                .orElseThrow(() -> new IllegalStateException(
                        ComputeServices.unavailableReason().orElse("Compute unavailable without reason")));
        ComputeDispatcher.Program initializeProgram = dispatcher.createProgram(
                new ComputeDispatcher.ProgramDescriptor("smoke_initialize", INITIALIZE_SHADER, 1));
        ComputeDispatcher.Program stressProgram = dispatcher.createProgram(
                new ComputeDispatcher.ProgramDescriptor("smoke_barrier_stress", STRESS_SHADER, 1));
        ComputeDispatcher.Buffer buffer = dispatcher.createBuffer(
                new ComputeDispatcher.BufferDescriptor("smoke_compute_data", WORDS * Integer.BYTES));
        SmokeComputeProbe probe = new SmokeComputeProbe(
                dispatcher, initializeProgram, stressProgram, buffer);
        probe.runInitialize("preReload");
        return probe;
    }

    List<Check> finishAfterReload() {
        if (dispatcher == null) {
            return List.copyOf(checks);
        }
        runInitialize("postReload");
        for (int i = 0; i < STRESS_ITERATIONS; i++) {
            dispatcher.dispatch(new ComputeDispatcher.Dispatch(
                    stressProgram, List.of(buffer), GROUPS, 1, 1));
        }
        dispatcher.submitAndWait();
        ByteBuffer result = buffer.read().order(ByteOrder.nativeOrder());
        int mismatches = 0;
        long checksum = 0L;
        for (int i = 0; i < WORDS; i++) {
            int expected = i * 3 + 7;
            for (int iteration = 0; iteration < STRESS_ITERATIONS; iteration++) {
                expected = expected * 1664525 + 1013904223;
            }
            int actual = result.getInt(i * Integer.BYTES);
            if (actual != expected) {
                mismatches++;
            }
            checksum += Integer.toUnsignedLong(actual);
        }
        checks.add(new Check(mismatches == 0,
                "computeBarrierStress dispatches=" + STRESS_ITERATIONS
                        + " words=" + WORDS + " mismatches=" + mismatches
                        + " checksum=" + Long.toUnsignedString(checksum)));
        return List.copyOf(checks);
    }

    private void runInitialize(String phase) {
        ByteBuffer zeros = ByteBuffer.allocateDirect(WORDS * Integer.BYTES).order(ByteOrder.nativeOrder());
        buffer.write(zeros);
        dispatcher.dispatch(new ComputeDispatcher.Dispatch(
                initializeProgram, List.of(buffer), GROUPS, 1, 1));
        dispatcher.submitAndWait();
        ByteBuffer result = buffer.read().order(ByteOrder.nativeOrder());
        int mismatches = 0;
        long checksum = 0L;
        for (int i = 0; i < WORDS; i++) {
            int actual = result.getInt(i * Integer.BYTES);
            if (actual != i * 3 + 7) {
                mismatches++;
            }
            checksum += Integer.toUnsignedLong(actual);
        }
        checks.add(new Check(mismatches == 0,
                "computeReadback phase=" + phase + " words=" + WORDS
                        + " mismatches=" + mismatches + " checksum=" + checksum));
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
        }
        if (stressProgram != null) {
            stressProgram.close();
        }
        if (initializeProgram != null) {
            initializeProgram.close();
        }
    }
}
