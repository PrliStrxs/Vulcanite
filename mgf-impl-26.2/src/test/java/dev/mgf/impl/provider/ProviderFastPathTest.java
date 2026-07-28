package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

final class ProviderFastPathTest {

    private static final int WARMUP_CALLS = 10_000;
    private static final int MEASURED_CALLS = 1_000_000;
    private static final Function<Object, Object> UNREACHABLE_ACTIVE_PATH = value -> {
        throw new AssertionError("inactive bridge entered the active path");
    };

    @Test
    void oneMillionInactiveCallsLeaveAllGpuCountersAtZero(TestReporter reporter) {
        Object original = new Object();
        ProviderFrameBridge.resetDiagnostics();

        for (int index = 0; index < WARMUP_CALLS; index++) {
            ProviderFrameBridge.beforeBlit(original, false, UNREACHABLE_ACTIVE_PATH);
        }

        long started = System.nanoTime();
        Object result = original;
        for (int index = 0; index < MEASURED_CALLS; index++) {
            result = ProviderFrameBridge.beforeBlit(original, false, UNREACHABLE_ACTIVE_PATH);
        }
        long elapsed = System.nanoTime() - started;

        ProviderFrameBridge.Diagnostics diagnostics = ProviderFrameBridge.diagnostics();
        assertSame(original, result);
        assertEquals(0, diagnostics.activeFrames());
        assertEquals(0, diagnostics.allocations());
        assertEquals(0, diagnostics.commandRecordings());
        assertEquals(0, diagnostics.copies());
        assertEquals(0, diagnostics.outputCopies());
        assertEquals(0, diagnostics.extraPresents());
        reporter.publishEntry("inactiveBridgeNanoseconds", Long.toString(elapsed));
    }
}
