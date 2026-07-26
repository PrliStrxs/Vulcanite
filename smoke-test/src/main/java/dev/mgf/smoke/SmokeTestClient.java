package dev.mgf.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.shaders.ShaderType;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Orchestrates the smoke run. CLIENT_STARTED fires before the initial resource
 * reload finishes (the shader compilation cache is still empty there), so the
 * harness instead polls each tick until the vanilla fullscreen shader resolves
 * — the exact readiness condition the pipeline checks depend on — then runs
 * {@link SmokeChecks}, writes {@code mgf-smoke-result.txt} (first line
 * PASS/FAIL), and stops the client for the Gradle {@code smokeTest} task.
 */
public final class SmokeTestClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Smoke");
    private static final String RESULT_FILE = "mgf-smoke-result.txt";
    private static final Identifier READINESS_PROBE = Identifier.withDefaultNamespace("core/screenquad");
    private static final int TIMEOUT_TICKS = 1200;

    private boolean finished;
    private int ticks;

    @Override
    public void onInitializeClient() {
        String expectedBackend = System.getProperty("mgf.smoke.expectedBackend", "vulkan");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) {
                return;
            }
            if (!shadersReady(client)) {
                if (++ticks >= TIMEOUT_TICKS) {
                    finished = true;
                    writeResult(false, List.of("shader cache never became ready (initial resource reload stuck?)"));
                    client.stop();
                }
                return;
            }
            finished = true;
            runChecks(client, expectedBackend);
        });
    }

    private static boolean shadersReady(Minecraft client) {
        try {
            return client.getShaderManager().getShader(READINESS_PROBE, ShaderType.VERTEX) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void runChecks(Minecraft client, String expectedBackend) {
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
