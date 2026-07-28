package dev.mgf.impl.provider;

import java.util.Objects;

import dev.mgf.api.provider.ProviderResult;

/** Tracks one provider role's consecutive failures for the current device session. */
final class ProviderFailureTracker {

    private static final int MAX_RECOVERABLE_FAILURES = 3;

    private int consecutiveRecoverableFailures;
    private boolean disabled;
    private String reasonCode = "";
    private String message = "";

    ProviderResult record(ProviderResult result) {
        Objects.requireNonNull(result, "result");
        if (disabled) {
            return result;
        }
        switch (result.code()) {
            case SUCCESS -> consecutiveRecoverableFailures = 0;
            case SKIPPED -> {
            }
            case RECOVERABLE_FAILURE -> {
                consecutiveRecoverableFailures++;
                if (consecutiveRecoverableFailures >= MAX_RECOVERABLE_FAILURES) {
                    disable(result);
                }
            }
            case FATAL_FAILURE -> disable(result);
        }
        return result;
    }

    ProviderResult recordException(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        String message = throwable.getClass().getSimpleName();
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            message += ": " + throwable.getMessage();
        }
        ProviderResult result = ProviderResult.fatal("provider_exception", message);
        record(result);
        return result;
    }

    int consecutiveRecoverableFailures() {
        return consecutiveRecoverableFailures;
    }

    boolean disabled() {
        return disabled;
    }

    String reasonCode() {
        return reasonCode;
    }

    String message() {
        return message;
    }

    private void disable(ProviderResult result) {
        disabled = true;
        reasonCode = result.reasonCode();
        message = result.message();
    }
}
