package dev.mgf.impl.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

final class CoreNeutralityTest {

    private static final List<String> CORE_FORBIDDEN = List.of(
            "ComputeEffects",
            "VulkanAutoExposure",
            "ComputeAutoExposureRegistry",
            "registerMainColorAutoExposure",
            "dev/mgf/samples/",
            "assets/mgf-sample-");

    private static final List<String> BOOTSTRAP_FORBIDDEN = List.of(
            "ComputeServices",
            "ComputeServiceRegistry",
            "VulkanComputeDispatcher",
            "ComputeAutoExposureRegistry",
            "VulkanAutoExposure");

    @Test
    void playerMainSourceSetContainsNoBundledEffectsOrSamples() throws IOException {
        Path project = projectDirectory();
        List<String> violations = scan(List.of(
                project.resolve("src/main/java"),
                project.resolve("src/main/resources"),
                project.resolve("build/classes/java/main"),
                project.resolve("build/resources/main")), CORE_FORBIDDEN);

        assertTrue(violations.isEmpty(), () -> "Player main source set is not neutral:\n"
                + String.join("\n", violations));
    }

    @Test
    void providerBootstrapDoesNotCreateVulkanComputeServices() throws IOException {
        Path project = projectDirectory();
        List<String> violations = scan(List.of(
                project.resolve("src/main/java/dev/mgf/impl/MgfClient.java"),
                project.resolve("src/main/java/dev/mgf/impl/provider/ProviderDiscovery.java"),
                project.resolve("build/classes/java/main/dev/mgf/impl/MgfClient.class"),
                project.resolve("build/classes/java/main/dev/mgf/impl/provider/ProviderDiscovery.class")),
                BOOTSTRAP_FORBIDDEN);

        assertTrue(violations.isEmpty(), () -> "Provider bootstrap references Vulkan compute creation:\n"
                + String.join("\n", violations));
    }

    private static List<String> scan(List<Path> roots, List<String> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String normalized = path.toString().replace('\\', '/');
                    String content = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                    for (String token : forbidden) {
                        if (normalized.contains(token) || content.contains(token)) {
                            violations.add(path + " contains " + token);
                        }
                    }
                }
            }
        }
        return violations;
    }

    private static Path projectDirectory() {
        Path working = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(working.resolve("src/main"))) {
            return working;
        }
        Path module = working.resolve("mgf-impl-26.2");
        if (Files.isDirectory(module.resolve("src/main"))) {
            return module;
        }
        throw new IllegalStateException("Could not locate the mgf-impl-26.2 project from " + working);
    }
}
