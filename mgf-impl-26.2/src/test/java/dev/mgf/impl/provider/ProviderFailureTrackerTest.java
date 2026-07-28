package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.mgf.api.provider.ProviderResult;
import dev.mgf.api.provider.ProviderResultCode;

final class ProviderFailureTrackerTest {

    @Test
    void successResetsRecoverableStrikesWhileSkippedDoesNotAddOne() {
        ProviderFailureTracker tracker = new ProviderFailureTracker();

        tracker.record(ProviderResult.recoverable("temporary", "Temporary failure"));
        tracker.record(ProviderResult.skipped("no_history", "History is not ready"));

        assertEquals(1, tracker.consecutiveRecoverableFailures());
        assertFalse(tracker.disabled());

        tracker.record(ProviderResult.success());

        assertEquals(0, tracker.consecutiveRecoverableFailures());
        assertFalse(tracker.disabled());
    }

    @Test
    void thirdConsecutiveRecoverableFailureDisablesRole() {
        ProviderFailureTracker tracker = new ProviderFailureTracker();

        tracker.record(ProviderResult.recoverable("temporary", "First failure"));
        tracker.record(ProviderResult.recoverable("temporary", "Second failure"));
        tracker.record(ProviderResult.recoverable("temporary", "Third failure"));

        assertTrue(tracker.disabled());
        assertEquals(3, tracker.consecutiveRecoverableFailures());
        assertEquals("temporary", tracker.reasonCode());
        assertEquals("Third failure", tracker.message());
    }

    @Test
    void fatalFailureDisablesImmediately() {
        ProviderFailureTracker tracker = new ProviderFailureTracker();

        tracker.record(ProviderResult.fatal("device_lost", "Device was lost"));

        assertTrue(tracker.disabled());
        assertEquals("device_lost", tracker.reasonCode());
    }

    @Test
    void exceptionBecomesFatalProviderException() {
        ProviderFailureTracker tracker = new ProviderFailureTracker();

        ProviderResult result = tracker.recordException(new IllegalStateException("SDK failed"));

        assertEquals(ProviderResultCode.FATAL_FAILURE, result.code());
        assertEquals("provider_exception", result.reasonCode());
        assertEquals("IllegalStateException: SDK failed", result.message());
        assertTrue(tracker.disabled());
    }
}
