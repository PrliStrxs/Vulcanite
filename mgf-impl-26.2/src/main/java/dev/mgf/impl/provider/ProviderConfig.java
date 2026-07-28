package dev.mgf.impl.provider;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import dev.mgf.api.provider.ProviderId;

/** Parsed provider-selection configuration. */
public record ProviderConfig(
        Choice upscaler,
        Choice frameGeneration,
        Choice presentHook) {

    public ProviderConfig {
        upscaler = Objects.requireNonNull(upscaler, "upscaler");
        frameGeneration = Objects.requireNonNull(frameGeneration, "frameGeneration");
        presentHook = Objects.requireNonNull(presentHook, "presentHook");
    }

    public static ProviderConfig defaults() {
        return new ProviderConfig(Choice.auto(), Choice.auto(), Choice.auto());
    }

    public static ProviderConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return new ProviderConfig(
                parse(properties.getProperty("upscaler", "auto")),
                parse(properties.getProperty("frame_generation", "auto")),
                parse(properties.getProperty("present_hook", "auto")));
    }

    private static Choice parse(String raw) {
        String value = raw.trim();
        if (value.equals("auto")) {
            return Choice.auto();
        }
        if (value.equals("off")) {
            return Choice.off();
        }
        try {
            return Choice.exact(new ProviderId(value));
        } catch (IllegalArgumentException exception) {
            return Choice.invalid(raw);
        }
    }

    public enum Mode {
        AUTO,
        OFF,
        EXACT
    }

    public record Choice(
            Mode mode,
            Optional<ProviderId> providerId,
            String reasonCode,
            String message) {

        public Choice {
            mode = Objects.requireNonNull(mode, "mode");
            providerId = Objects.requireNonNull(providerId, "providerId");
            reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
            message = Objects.requireNonNull(message, "message");
            if ((mode == Mode.EXACT) != providerId.isPresent()) {
                throw new IllegalArgumentException("exact choice must contain one provider ID");
            }
        }

        public static Choice auto() {
            return new Choice(Mode.AUTO, Optional.empty(), "auto", "Select the best supported provider");
        }

        public static Choice off() {
            return new Choice(Mode.OFF, Optional.empty(), "off", "Provider role is disabled");
        }

        public static Choice exact(ProviderId providerId) {
            return new Choice(Mode.EXACT, Optional.of(providerId),
                    "configured", "Use configured provider " + providerId);
        }

        static Choice invalid(String value) {
            return new Choice(Mode.OFF, Optional.empty(), "invalid_config",
                    "Invalid provider selection: " + value);
        }
    }
}
