package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.mgf.api.provider.ProviderId;

final class ProviderConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileUsesAutoForEveryRole() throws IOException {
        ProviderConfig config = ProviderConfig.load(temporaryDirectory.resolve("missing.properties"));

        assertEquals(ProviderConfig.Mode.AUTO, config.upscaler().mode());
        assertEquals(ProviderConfig.Mode.AUTO, config.frameGeneration().mode());
        assertEquals(ProviderConfig.Mode.AUTO, config.presentHook().mode());
        assertEquals(false, config.experimentalFrameGeneration());
    }

    @Test
    void parsesExactOffAndAutoChoices() throws IOException {
        Path file = temporaryDirectory.resolve("mgf-providers.properties");
        Files.writeString(file, """
                upscaler=example:upscale
                frame_generation=off
                present_hook=auto
                experimental_frame_generation=true
                """);

        ProviderConfig config = ProviderConfig.load(file);

        assertEquals(ProviderConfig.Mode.EXACT, config.upscaler().mode());
        assertEquals(new ProviderId("example:upscale"), config.upscaler().providerId().orElseThrow());
        assertEquals(ProviderConfig.Mode.OFF, config.frameGeneration().mode());
        assertEquals(ProviderConfig.Mode.AUTO, config.presentHook().mode());
        assertEquals(true, config.experimentalFrameGeneration());
    }

    @Test
    void invalidChoiceFailsSoftToOff() throws IOException {
        Path file = temporaryDirectory.resolve("mgf-providers.properties");
        Files.writeString(file, "upscaler=BAD VALUE\n");

        ProviderConfig config = ProviderConfig.load(file);

        assertEquals(ProviderConfig.Mode.OFF, config.upscaler().mode());
        assertEquals("invalid_config", config.upscaler().reasonCode());
        assertTrue(config.upscaler().message().contains("BAD VALUE"));
    }
}
