package dev.mgf.api.provider;

import java.util.Objects;

/** Immutable provider metadata used for validation and deterministic selection. */
public record ProviderDescriptor(
        ProviderId id,
        String displayName,
        String providerVersion,
        int priority,
        int minimumApiMajor,
        int minimumApiMinor) {

    public ProviderDescriptor {
        id = Objects.requireNonNull(id, "id");
        displayName = requireText(displayName, "displayName");
        providerVersion = requireText(providerVersion, "providerVersion");
        if (minimumApiMajor < 0 || minimumApiMinor < 0) {
            throw new IllegalArgumentException("minimum API version components must be non-negative");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
