package com.github.slmpc.lumingraphics.mc.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

final class SourceBaselineTest {
    private static final List<String> REQUIRED_TYPES = List.of(
            "GpuBuffer", "GlBuffer", "GpuTexture", "GpuTextureView", "GlTexture", "GlTextureView",
            "GlShaderModule", "GlProgram", "GlDevice", "GlRenderPipeline", "RenderPipeline", "CompiledRenderPipeline",
            "CommandEncoder", "RenderPass", "GpuDevice", "RenderSystem");

    @ParameterizedTest
    @ValueSource(strings = {"26.1.2", "26.2"})
    void selectedSourcesMatchVersionHashesAndStructuredSignatures(String version) throws Exception {
        String configured = System.getProperty("baseline." + version, version);
        Path root = Path.of(System.getProperty("baseline.root." + version, Path.of("reference", "vanilla-" + configured).toString()));
        Properties manifest = load(root.resolve("manifest.properties"));
        assertEquals(version, manifest.getProperty("minecraft.version"), "version mismatch for requested " + version);
        assertFalse(manifest.getProperty("origin.artifact", "").isBlank(), "missing origin artifact");
        assertFalse(manifest.getProperty("origin.url", "").isBlank(), "missing origin URL");
        for (String type : REQUIRED_TYPES) {
            String relative = manifest.getProperty("type." + type + ".source");
            assertFalse(relative == null || relative.isBlank(), "missing source mapping for " + type);
            Path source = root.resolve(relative);
            byte[] bytes = Files.readAllBytes(source);
            assertEquals(manifest.getProperty("type." + type + ".sha256"), sourceSha256(bytes), "hash mismatch: " + type);
            assertEquals(manifest.getProperty("type." + type + ".signature"), StructuredSourceParser.summary(type, bytes),
                    "signature mismatch: " + type);
        }
    }

    @Test
    void malformedSourceProducesStructuredDiagnostic() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StructuredSourceParser.summary("Broken", "package test; public class Broken {".getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("malformed Java source"), error::getMessage);
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String sourceSha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] normalized = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        return sha256(normalized);
    }
}
