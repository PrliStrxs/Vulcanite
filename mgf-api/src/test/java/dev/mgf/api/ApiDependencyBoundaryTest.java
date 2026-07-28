package dev.mgf.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

final class ApiDependencyBoundaryTest {

    private static final List<String> FORBIDDEN = List.of(
            "com/mojang/", "net/minecraft/", "org/lwjgl/", "net/fabricmc/",
            "dev/mgf/impl/");

    @Test
    void compiledApiContainsNoImplementationDependencies() throws IOException {
        Path classes = Path.of("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(classes), "main API classes must be compiled before tests");

        try (Stream<Path> files = Files.walk(classes)) {
            List<Path> classFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .toList();
            assertFalse(classFiles.isEmpty(), "expected compiled API classes");
            for (Path classFile : classFiles) {
                String constantPool = Files.readString(classFile, StandardCharsets.ISO_8859_1);
                for (String forbidden : FORBIDDEN) {
                    assertFalse(constantPool.contains(forbidden),
                            () -> classFile + " references forbidden package " + forbidden);
                }
            }
        }
    }
}
