package dev.mgf.api.upscale;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.ProviderSupport;

/** Upscaler probe result with capabilities when available. */
public record UpscalerSupport(
        ProviderSupport status,
        Optional<UpscalerCapabilities> capabilities,
        Optional<UpscalerRequirements> requirements) {

    public UpscalerSupport {
        status = Objects.requireNonNull(status, "status");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        requirements = Objects.requireNonNull(requirements, "requirements");
        if (status.supported() != (capabilities.isPresent() && requirements.isPresent())) {
            throw new IllegalArgumentException("support status must match capability availability");
        }
    }

    public static UpscalerSupport available(
            UpscalerCapabilities capabilities, UpscalerRequirements requirements) {
        return new UpscalerSupport(ProviderSupport.available(),
                Optional.of(capabilities), Optional.of(requirements));
    }

    public static UpscalerSupport unavailable(String reasonCode, String message) {
        return new UpscalerSupport(ProviderSupport.unsupported(reasonCode, message),
                Optional.empty(), Optional.empty());
    }
}
