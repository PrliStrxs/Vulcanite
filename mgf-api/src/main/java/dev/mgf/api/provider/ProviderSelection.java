package dev.mgf.api.provider;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only provider registration and selection state for one role. */
public record ProviderSelection(
        ProviderKind kind,
        List<ProviderId> registered,
        Optional<ProviderId> selected,
        ProviderSessionState state,
        String reasonCode,
        String message) {

    public ProviderSelection {
        kind = Objects.requireNonNull(kind, "kind");
        registered = List.copyOf(registered);
        selected = Objects.requireNonNull(selected, "selected");
        state = Objects.requireNonNull(state, "state");
        reasonCode = requireText(reasonCode, "reasonCode");
        message = requireText(message, "message");
        if (selected.isPresent() && !registered.contains(selected.get())) {
            throw new IllegalArgumentException("selected provider must be registered");
        }
    }

    public static ProviderSelection off(ProviderKind kind) {
        return new ProviderSelection(kind, List.of(), Optional.empty(),
                ProviderSessionState.OFF, "off", "Provider role is disabled");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
