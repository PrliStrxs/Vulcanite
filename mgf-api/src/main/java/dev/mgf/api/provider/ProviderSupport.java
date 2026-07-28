package dev.mgf.api.provider;

import java.util.Objects;

/** Result of probing a provider against the live device and frame inputs. */
public record ProviderSupport(boolean supported, String reasonCode, String message) {

    private static final ProviderSupport SUPPORTED = new ProviderSupport(true, "", "");

    public ProviderSupport {
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        message = Objects.requireNonNull(message, "message");
        if (supported && (!reasonCode.isEmpty() || !message.isEmpty())) {
            throw new IllegalArgumentException("supported result must not contain a failure reason");
        }
        if (!supported && (reasonCode.isBlank() || message.isBlank())) {
            throw new IllegalArgumentException("unsupported result requires a reason code and message");
        }
    }

    public static ProviderSupport available() {
        return SUPPORTED;
    }

    public static ProviderSupport unsupported(String reasonCode, String message) {
        return new ProviderSupport(false, reasonCode, message);
    }
}
