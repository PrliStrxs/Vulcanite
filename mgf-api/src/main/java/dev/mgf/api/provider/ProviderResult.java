package dev.mgf.api.provider;

import java.util.Objects;

/** Fail-soft result returned by frame-producing provider callbacks. */
public record ProviderResult(ProviderResultCode code, String reasonCode, String message) {

    private static final ProviderResult SUCCESS = new ProviderResult(
            ProviderResultCode.SUCCESS, "", "");

    public ProviderResult {
        code = Objects.requireNonNull(code, "code");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        message = Objects.requireNonNull(message, "message");
        if (code == ProviderResultCode.SUCCESS && (!reasonCode.isEmpty() || !message.isEmpty())) {
            throw new IllegalArgumentException("success result must not contain a reason");
        }
        if (code != ProviderResultCode.SUCCESS && (reasonCode.isBlank() || message.isBlank())) {
            throw new IllegalArgumentException("non-success result requires a reason code and message");
        }
    }

    public static ProviderResult success() {
        return SUCCESS;
    }

    public static ProviderResult skipped(String reasonCode, String message) {
        return new ProviderResult(ProviderResultCode.SKIPPED, reasonCode, message);
    }

    public static ProviderResult recoverable(String reasonCode, String message) {
        return new ProviderResult(ProviderResultCode.RECOVERABLE_FAILURE, reasonCode, message);
    }

    public static ProviderResult fatal(String reasonCode, String message) {
        return new ProviderResult(ProviderResultCode.FATAL_FAILURE, reasonCode, message);
    }
}
