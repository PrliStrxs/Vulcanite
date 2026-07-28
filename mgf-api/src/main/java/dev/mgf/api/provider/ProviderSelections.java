package dev.mgf.api.provider;

import java.util.Objects;

/** Read-only snapshot of all provider roles. */
public record ProviderSelections(
        ProviderSelection upscaler,
        ProviderSelection frameGeneration,
        ProviderSelection presentHook) {

    public ProviderSelections {
        upscaler = requireKind(upscaler, ProviderKind.UPSCALER, "upscaler");
        frameGeneration = requireKind(frameGeneration, ProviderKind.FRAME_GENERATION, "frameGeneration");
        presentHook = requireKind(presentHook, ProviderKind.PRESENT_HOOK, "presentHook");
    }

    public static ProviderSelections off() {
        return new ProviderSelections(
                ProviderSelection.off(ProviderKind.UPSCALER),
                ProviderSelection.off(ProviderKind.FRAME_GENERATION),
                ProviderSelection.off(ProviderKind.PRESENT_HOOK));
    }

    private static ProviderSelection requireKind(
            ProviderSelection value, ProviderKind kind, String name) {
        Objects.requireNonNull(value, name);
        if (value.kind() != kind) {
            throw new IllegalArgumentException(name + " has kind " + value.kind() + ", expected " + kind);
        }
        return value;
    }
}
