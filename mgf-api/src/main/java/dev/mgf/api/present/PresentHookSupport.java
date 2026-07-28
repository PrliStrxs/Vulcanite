package dev.mgf.api.present;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.ProviderSupport;

/** PresentHook probe result with capabilities when available. */
public record PresentHookSupport(
        ProviderSupport status,
        Optional<PresentHookCapabilities> capabilities) {

    public PresentHookSupport {
        status = Objects.requireNonNull(status, "status");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        if (status.supported() != capabilities.isPresent()) {
            throw new IllegalArgumentException("support status must match capability availability");
        }
    }

    public static PresentHookSupport available(PresentHookCapabilities capabilities) {
        return new PresentHookSupport(ProviderSupport.available(), Optional.of(capabilities));
    }

    public static PresentHookSupport unavailable(String reasonCode, String message) {
        return new PresentHookSupport(ProviderSupport.unsupported(reasonCode, message), Optional.empty());
    }
}
