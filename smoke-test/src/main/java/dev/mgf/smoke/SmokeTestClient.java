package dev.mgf.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

/**
 * Orchestrates the smoke run: at CLIENT_STARTED (device and MGF fully up),
 * runs {@link SmokeChecks}, writes {@code mgf-smoke-result.txt} into the run
 * directory (first line PASS/FAIL), and stops the client so the Gradle
 * {@code smokeTest} task can verify the result.
 */
public final class SmokeTestClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Smoke");
    private static final String RESULT_FILE = "mgf-smoke-result.txt";

    @Override
    public void onInitializeClient() {
        String expectedBackend = System.getProperty("mgf.smoke.expectedBackend", "vulkan");
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            SmokeChecks checks;
            try {
                checks = SmokeChecks.run(expectedBackend);
            } catch (Throwable t) {
                LOGGER.error("Smoke checks threw", t);
                writeResult(false, List.of("exception: " + t));
                client.stop();
                return;
            }
            LOGGER.info("Smoke checks {}:\n{}", checks.passed() ? "PASSED" : "FAILED",
                    String.join("\n", checks.lines()));
            writeResult(checks.passed(), checks.lines());
            client.stop();
        });
    }

    private static void writeResult(boolean passed, List<String> details) {
        List<String> lines = new ArrayList<>();
        lines.add(passed ? "PASS" : "FAIL");
        lines.addAll(details);
        try {
            // Run directory is the process working directory in Loom dev runs.
            Files.write(Path.of(RESULT_FILE), lines);
        } catch (IOException e) {
            LOGGER.error("Could not write {}", RESULT_FILE, e);
        }
    }
}
