package dev.mgf.api.provider;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable namespace-qualified identity for one provider implementation. */
public record ProviderId(String value) implements Comparable<ProviderId> {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public ProviderId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid provider ID: " + value);
        }
    }

    @Override
    public int compareTo(ProviderId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
