package com.github.slmpc.lumingraphics.mc.baseline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GeneratedBaselineToolTest {
    @TempDir Path temporary;

    @Test
    void generationFailsWhenBothToolOutputsAreMissing() throws Exception {
        var error = assertThrows(IllegalStateException.class,
                () -> generate(temporary.resolve("missing-loom.jar"), temporary.resolve("missing-moddev.jar"), false));
        assertTrue(error.getMessage().startsWith("LOOM_OUTPUT_MISSING:"), error::getMessage);
    }

    @Test
    void generationFailsWhenOnlyLoomOutputExists() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        var error = assertThrows(IllegalStateException.class,
                () -> generate(loom, temporary.resolve("missing-moddev.jar"), false));
        assertTrue(error.getMessage().startsWith("MODDEV_OUTPUT_MISSING:"), error::getMessage);
    }

    @Test
    void generationFailsWhenOnlyModDevOutputExists() throws Exception {
        Path moddev = sourceJar("moddev.jar", null, false);
        var error = assertThrows(IllegalStateException.class,
                () -> generate(temporary.resolve("missing-loom.jar"), moddev, false));
        assertTrue(error.getMessage().startsWith("LOOM_OUTPUT_MISSING:"), error::getMessage);
    }

    @Test
    void generationFailsWhenOriginHashIsTampered() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        Path moddev = sourceJar("moddev.jar", null, false);
        var error = assertThrows(IllegalStateException.class, () -> generate(loom, moddev, true));
        assertTrue(error.getMessage().startsWith("ORIGIN_SHA_MISMATCH:"), error::getMessage);
    }

    @Test
    void generationFailsWhenToolSignaturesDiverge() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        Path moddev = sourceJar("moddev.jar", "GpuBuffer", true);
        var error = assertThrows(IllegalStateException.class, () -> generate(loom, moddev, false));
        assertTrue(error.getMessage().startsWith("SEMANTIC_DIVERGENCE:"), error::getMessage);
    }

    @Test
    void generationAllowsAccessWidenerVisibilityDifference() throws Exception {
        Path loom = sourceJarWithConstructorVisibility("loom.jar", "GlBuffer", "public");
        Path moddev = sourceJarWithConstructorVisibility("moddev.jar", "GlBuffer", "protected");
        assertDoesNotThrow(() -> generate(loom, moddev, false));
    }

    @Test
    void generationAllowsEquivalentNestedTypeQualification() throws Exception {
        Path loom = sourceJarWithTypeSource("loom.jar", "GlTexture", """
                package example;
                import example.FrameBufferCache.CacheKey;
                public class GlTexture { CacheKey key; void accept(CacheKey key) {} }
                """);
        Path moddev = sourceJarWithTypeSource("moddev.jar", "GlTexture", """
                package example;
                public class GlTexture {
                    FrameBufferCache.CacheKey key;
                    void accept(FrameBufferCache.CacheKey key) {}
                }
                """);
        assertDoesNotThrow(() -> generate(loom, moddev, false));
    }

    @Test
    void generationAllowsEquivalentTypeAnnotationPlacement() throws Exception {
        Path loom = sourceJarWithTypeSource("loom.jar", "RenderPass", """
                package example;
                public class RenderPass { void accept(@Nullable Outer.Inner value) {} }
                """);
        Path moddev = sourceJarWithTypeSource("moddev.jar", "RenderPass", """
                package example;
                public class RenderPass { void accept(Outer.@Nullable Inner value) {} }
                """);
        assertDoesNotThrow(() -> generate(loom, moddev, false));
    }

    @Test
    void generationFailsWhenMethodAccessContractDiffers() throws Exception {
        Path loom = sourceJarWithTypeSource("loom.jar", "GlBuffer", """
                package example;
                public class GlBuffer { public void run() {} }
                """);
        Path moddev = sourceJarWithTypeSource("moddev.jar", "GlBuffer", """
                package example;
                public class GlBuffer { protected void run() {} }
                """);
        var error = assertThrows(IllegalStateException.class, () -> generate(loom, moddev, false));
        assertTrue(error.getMessage().startsWith("SEMANTIC_DIVERGENCE:"), error::getMessage);
    }

    @Test
    void generationFailsWhenDistinctImportedTypesShareSimpleName() throws Exception {
        Path loom = sourceJarWithTypeSource("loom.jar", "GlTexture", """
                package example;
                import alpha.CacheKey;
                public class GlTexture { CacheKey key; }
                """);
        Path moddev = sourceJarWithTypeSource("moddev.jar", "GlTexture", """
                package example;
                import beta.CacheKey;
                public class GlTexture { CacheKey key; }
                """);
        var error = assertThrows(IllegalStateException.class, () -> generate(loom, moddev, false));
        assertTrue(error.getMessage().startsWith("SEMANTIC_DIVERGENCE:"), error::getMessage);
    }

    @Test
    void generationFailsWhenSelectedSourceIsAbsent() throws Exception {
        Path loom = sourceJar("loom.jar", "GpuBuffer", false);
        Path moddev = sourceJar("moddev.jar", null, false);
        var error = assertThrows(IllegalStateException.class, () -> generate(loom, moddev, false));
        assertTrue(error.getMessage().startsWith("GENERATED_SOURCE_MISSING:"), error::getMessage);
    }

    @Test
    void completeIndependentOutputsProduceDeterministicManifest() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        Path moddev = sourceJar("moddev.jar", null, false);
        assertDoesNotThrow(() -> generate(loom, moddev, false));
        String first = Files.readString(temporary.resolve("output/manifest.properties"));
        assertDoesNotThrow(() -> generate(loom, moddev, false));
        assertTrue(first.equals(Files.readString(temporary.resolve("output/manifest.properties"))));
    }

    @Test
    void verificationFailsWhenCopiedArtifactIsTampered() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        Path moddev = sourceJar("moddev.jar", null, false);
        generate(loom, moddev, false);
        Path reference = createReference();
        Files.writeString(temporary.resolve("output/artifacts/loom-sources.jar"), "tampered");
        var error = assertThrows(IllegalStateException.class, () -> verify(reference));
        assertTrue(error.getMessage().startsWith("ARTIFACT_SHA_MISMATCH:"), error::getMessage);
    }

    @Test
    void verificationFailsWhenSelectedGeneratedSourceIsDeleted() throws Exception {
        Path loom = sourceJar("loom.jar", null, false);
        Path moddev = sourceJar("moddev.jar", null, false);
        generate(loom, moddev, false);
        Path reference = createReference();
        Files.delete(temporary.resolve("output/loom/example/GpuBuffer.java"));
        var error = assertThrows(IllegalStateException.class, () -> verify(reference));
        assertTrue(error.getMessage().startsWith("GENERATED_SOURCE_MISSING:"), error::getMessage);
    }

    private void generate(Path loom, Path moddev, boolean wrongOriginHash) throws Exception {
        Path origin = temporary.resolve("neoform.zip");
        Files.writeString(origin, "origin", StandardCharsets.UTF_8);
        GeneratedBaselineTool.main(new String[] {"generate", "test", "1.15.5", "2.0.140",
                "net.neoforged:neoform:test@zip", "https://example.invalid/neoform.zip", origin.toString(),
                wrongOriginHash ? "0".repeat(64) : sha256(origin), loom.toString(), moddev.toString(),
                temporary.resolve("output").toString()});
    }

    private Path sourceJar(String name, String specialType, boolean divergent) throws IOException {
        Path path = temporary.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (String type : GeneratedBaselineTool.REQUIRED_TYPES) {
                if (type.equals(specialType) && !divergent) continue;
                output.putNextEntry(new JarEntry("example/" + type + ".java"));
                String extra = type.equals(specialType) && divergent ? " public String changed() { return \"x\"; }" : "";
                output.write(("package example; public class " + type
                        + " { public int value; public void run() {}" + extra + " }").getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private Path sourceJarWithConstructorVisibility(String name, String constructorType, String visibility)
            throws IOException {
        Path path = temporary.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (String type : GeneratedBaselineTool.REQUIRED_TYPES) {
                output.putNextEntry(new JarEntry("example/" + type + ".java"));
                String constructor = type.equals(constructorType) ? " " + visibility + " " + type + "() {}" : "";
                output.write(("package example; public class " + type
                        + " { public int value; public void run() {}" + constructor + " }")
                        .getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private Path sourceJarWithTypeSource(String name, String specialType, String specialSource) throws IOException {
        Path path = temporary.resolve(name);
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            for (String type : GeneratedBaselineTool.REQUIRED_TYPES) {
                output.putNextEntry(new JarEntry("example/" + type + ".java"));
                String source = type.equals(specialType) ? specialSource
                        : "package example; public class " + type + " { public int value; public void run() {} }";
                output.write(source.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private Path createReference() throws Exception {
        Path output = temporary.resolve("output");
        Properties generated = new Properties();
        try (var reader = Files.newBufferedReader(output.resolve("manifest.properties"))) {
            generated.load(reader);
        }
        Path reference = temporary.resolve("reference");
        Files.createDirectories(reference);
        Properties manifest = new Properties();
        for (String key : new String[] {"minecraft.version", "origin.artifact", "origin.url", "origin.sha256"}) {
            manifest.setProperty(key, generated.getProperty(key));
        }
        manifest.setProperty("generated.sources.sha256", generated.getProperty("moddev.sources.sha256"));
        for (String type : GeneratedBaselineTool.REQUIRED_TYPES) {
            String generatedPath = generated.getProperty("type." + type + ".moddev.source");
            Path source = output.resolve(generatedPath);
            Path destination = reference.resolve("example/" + type + ".java");
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination);
            byte[] bytes = Files.readAllBytes(destination);
            manifest.setProperty("type." + type + ".source", "example/" + type + ".java");
            manifest.setProperty("type." + type + ".sha256", generated.getProperty("type." + type + ".moddev.sha256"));
            manifest.setProperty("type." + type + ".signature", StructuredSourceParser.summary(type, bytes));
        }
        try (var writer = Files.newBufferedWriter(reference.resolve("manifest.properties"))) {
            manifest.store(writer, null);
        }
        return reference;
    }

    private void verify(Path reference) throws Exception {
        GeneratedBaselineTool.main(new String[] {"verify", temporary.resolve("output").toString(), reference.toString()});
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
