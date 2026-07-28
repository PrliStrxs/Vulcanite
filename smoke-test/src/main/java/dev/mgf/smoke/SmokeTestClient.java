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

import dev.mgf.impl.pipeline.PipelineWarmupRegistry;
import dev.mgf.samples.interop.SampleWorldGeometry;

/**
 * Orchestrates the smoke run. CLIENT_STARTED fires before the initial resource
 * reload finishes (the shader compilation cache is still empty there), so the
 * harness instead polls each tick until the vanilla fullscreen shader resolves,
 * triggers and awaits a second resource reload, then runs {@link SmokeChecks}.
 * This proves registered custom pipelines survive cache replacement before the
 * harness writes {@code mgf-smoke-result.txt} and stops the client.
 */
public final class SmokeTestClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MGF-Smoke");
    private static final String RESULT_FILE = "mgf-smoke-result.txt";
    private static final String SHUTDOWN_RESULT_FILE = "mgf-smoke-shutdown-result.txt";
    private static final Identifier READINESS_PROBE = Identifier.withDefaultNamespace("core/screenquad");
    private static final int TIMEOUT_TICKS = 1200;

    private boolean finished;
    private boolean reloadStarted;
    private int ticks;
    private SmokeComputeProbe computeProbe;

    @Override
    public void onInitializeClient() {
        String expectedBackend = System.getProperty("mgf.smoke.expectedBackend", "vulkan");
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> SmokeProviderProbe.writeShutdownResult(
                        Path.of(SHUTDOWN_RESULT_FILE), expectedBackend),
                "MGF smoke shutdown verifier"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (finished) {
                return;
            }
            if (reloadStarted) {
                if (++ticks >= TIMEOUT_TICKS) {
                    finished = true;
                    writeResult(false, List.of("second resource reload did not complete"));
                    client.stop();
                }
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
            reloadStarted = true;
            ticks = 0;
            try {
                computeProbe = SmokeComputeProbe.prepare(expectedBackend);
            } catch (Throwable t) {
                finished = true;
                LOGGER.error("Pre-reload compute probe failed", t);
                writeResult(false, List.of("pre-reload compute probe failed: " + t));
                client.stop();
                return;
            }
            long generationBeforeReload = PipelineWarmupRegistry.generation(SampleWorldGeometry.PIPELINE);
            client.reloadResourcePacks().whenCompleteAsync((unused, error) -> {
                if (error != null) {
                    finished = true;
                    LOGGER.error("Second resource reload failed", error);
                    writeResult(false, List.of("second resource reload failed: " + error));
                    client.stop();
                    return;
                }
                finished = true;
                runChecks(client, expectedBackend, generationBeforeReload, computeProbe);
            }, client);
        });
    }

    private static boolean shadersReady(Minecraft client) {
        try {
            return client.getShaderManager().getShader(READINESS_PROBE, ShaderType.VERTEX) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void runChecks(Minecraft client, String expectedBackend,
                                  long generationBeforeReload, SmokeComputeProbe computeProbe) {
        SmokeChecks checks;
        try (computeProbe) {
            checks = SmokeChecks.run(expectedBackend, generationBeforeReload,
                    computeProbe.finishAfterReload());
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
