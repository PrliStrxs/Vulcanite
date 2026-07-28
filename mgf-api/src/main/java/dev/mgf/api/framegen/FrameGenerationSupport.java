package dev.mgf.api.framegen;

import java.util.Objects;
import java.util.Optional;

import dev.mgf.api.provider.ProviderSupport;

/** Frame-generation probe result with capabilities when available. */
public record FrameGenerationSupport(
        ProviderSupport status,
        Optional<FrameGenerationCapabilities> capabilities,
        Optional<FrameGenerationRequirements> requirements) {

    public FrameGenerationSupport {
        status = Objects.requireNonNull(status, "status");
        capabilities = Objects.requireNonNull(capabilities, "capabilities");
        requirements = Objects.requireNonNull(requirements, "requirements");
        if (status.supported() != (capabilities.isPresent() && requirements.isPresent())) {
            throw new IllegalArgumentException("support status must match capability availability");
        }
    }

    public static FrameGenerationSupport available(
            FrameGenerationCapabilities capabilities,
            FrameGenerationRequirements requirements) {
        return new FrameGenerationSupport(ProviderSupport.available(),
                Optional.of(capabilities), Optional.of(requirements));
    }

    public static FrameGenerationSupport unavailable(String reasonCode, String message) {
        return new FrameGenerationSupport(ProviderSupport.unsupported(reasonCode, message),
                Optional.empty(), Optional.empty());
    }
}
