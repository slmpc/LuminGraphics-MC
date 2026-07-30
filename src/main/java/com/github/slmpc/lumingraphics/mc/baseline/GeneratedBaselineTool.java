package com.github.slmpc.lumingraphics.mc.baseline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

public final class GeneratedBaselineTool {
    static final List<String> REQUIRED_TYPES = List.of(
            "GpuBuffer", "GlBuffer", "GpuTexture", "GpuTextureView", "GlTexture", "GlTextureView",
            "GlShaderModule", "GlProgram", "GlDevice", "GlRenderPipeline", "RenderPipeline",
            "CompiledRenderPipeline", "CommandEncoder", "RenderPass", "GpuDevice", "RenderSystem");

    private GeneratedBaselineTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new BaselineException(Failure.INVALID_ARGUMENTS, "missing mode");
        }
        switch (args[0]) {
            case "generate" -> generate(args);
            case "verify" -> verify(args);
            default -> throw new BaselineException(Failure.INVALID_ARGUMENTS, "unknown mode: " + args[0]);
        }
    }

    private static void generate(String[] args) throws Exception {
        if (args.length != 11) {
            throw new BaselineException(Failure.INVALID_ARGUMENTS,
                    "generate expects version, tool versions, origin metadata, three artifacts, and output directory");
        }
        String version = args[1];
        String expectedOriginSha = args[7];
        Path loomJar = requireFile(Path.of(args[8]), Failure.LOOM_OUTPUT_MISSING);
        Path modDevJar = requireFile(Path.of(args[9]), Failure.MODDEV_OUTPUT_MISSING);
        Path originZip = requireFile(Path.of(args[6]), Failure.ORIGIN_MISSING);
        if (!sha256(originZip).equals(expectedOriginSha)) {
            throw new BaselineException(Failure.ORIGIN_SHA_MISMATCH,
                    "expected " + expectedOriginSha + " but got " + sha256(originZip));
        }

        Path output = Path.of(args[10]);
        Files.createDirectories(output.resolve("artifacts"));
        copy(originZip, output.resolve("artifacts/neoform.zip"));
        copy(loomJar, output.resolve("artifacts/loom-sources.jar"));
        copy(modDevJar, output.resolve("artifacts/moddev-sources.jar"));

        List<String> manifest = new ArrayList<>();
        add(manifest, "minecraft.version", version);
        add(manifest, "loom.version", args[2]);
        add(manifest, "moddev.version", args[3]);
        add(manifest, "origin.artifact", args[4]);
        add(manifest, "origin.url", args[5]);
        add(manifest, "origin.path", "artifacts/neoform.zip");
        add(manifest, "origin.sha256", expectedOriginSha);
        add(manifest, "loom.sources.path", "artifacts/loom-sources.jar");
        add(manifest, "loom.sources.sha256", sha256(loomJar));
        add(manifest, "moddev.sources.path", "artifacts/moddev-sources.jar");
        add(manifest, "moddev.sources.sha256", sha256(modDevJar));

        for (String type : REQUIRED_TYPES) {
            ExtractedSource loom = extract(loomJar, type, output.resolve("loom"));
            ExtractedSource modDev = extract(modDevJar, type, output.resolve("moddev"));
            String loomSignature = StructuredSourceParser.semanticSignature(type, loom.bytes());
            String modDevSignature = StructuredSourceParser.semanticSignature(type, modDev.bytes());
            if (!loomSignature.equals(modDevSignature)) {
                throw new BaselineException(Failure.SEMANTIC_DIVERGENCE, type + " differs between Loom and ModDev");
            }
            addSource(manifest, type, "loom", loom, loomSignature);
            addSource(manifest, type, "moddev", modDev, modDevSignature);
        }
        writeManifest(output.resolve("manifest.properties"), manifest);
    }

    private static void verify(String[] args) throws Exception {
        if (args.length != 3) {
            throw new BaselineException(Failure.INVALID_ARGUMENTS, "verify expects generated and reference directories");
        }
        Path generated = Path.of(args[1]);
        Path reference = Path.of(args[2]);
        Properties actual = load(generated.resolve("manifest.properties"), Failure.GENERATED_MANIFEST_MISSING);
        Properties expected = load(reference.resolve("manifest.properties"), Failure.REFERENCE_MANIFEST_MISSING);

        verifyArtifact(actual, generated, "origin", Failure.ORIGIN_SHA_MISMATCH);
        verifyArtifact(actual, generated, "loom.sources", Failure.ARTIFACT_SHA_MISMATCH);
        verifyArtifact(actual, generated, "moddev.sources", Failure.ARTIFACT_SHA_MISMATCH);
        equal(expected, actual, "minecraft.version", Failure.REFERENCE_MISMATCH);
        equal(expected, actual, "origin.artifact", Failure.REFERENCE_MISMATCH);
        equal(expected, actual, "origin.url", Failure.REFERENCE_MISMATCH);
        equal(expected, actual, "origin.sha256", Failure.ORIGIN_SHA_MISMATCH);
        requireEquals(expected.getProperty("generated.sources.sha256"), actual.getProperty("moddev.sources.sha256"),
                Failure.ARTIFACT_SHA_MISMATCH, "reference generated.sources.sha256");

        for (String type : REQUIRED_TYPES) {
            String referencePath = required(expected, "type." + type + ".source", Failure.REFERENCE_MISMATCH);
            byte[] referenceBytes = Files.readAllBytes(requireFile(reference.resolve(referencePath), Failure.REFERENCE_SOURCE_MISSING));
            String referenceHash = sourceSha256(referenceBytes);
            requireEquals(required(expected, "type." + type + ".sha256", Failure.REFERENCE_MISMATCH), referenceHash,
                    Failure.REFERENCE_MISMATCH, type + " reference SHA");
            requireEquals(actual.getProperty("type." + type + ".moddev.sha256"), referenceHash,
                    Failure.REFERENCE_MISMATCH, type + " generated ModDev SHA");
            String summary = StructuredSourceParser.summary(type, referenceBytes);
            requireEquals(required(expected, "type." + type + ".signature", Failure.REFERENCE_MISMATCH), summary,
                    Failure.REFERENCE_MISMATCH, type + " reference summary");
            String semantic = StructuredSourceParser.semanticSignature(type, referenceBytes);
            requireEquals(actual.getProperty("type." + type + ".loom.signature"), semantic,
                    Failure.SEMANTIC_DIVERGENCE, type + " Loom/reference signature");
            requireEquals(actual.getProperty("type." + type + ".moddev.signature"), semantic,
                    Failure.SEMANTIC_DIVERGENCE, type + " ModDev/reference signature");
            verifyGeneratedSource(actual, generated, type, "loom");
            verifyGeneratedSource(actual, generated, type, "moddev");
        }
    }

    private static void verifyGeneratedSource(Properties manifest, Path root, String type, String tool) throws Exception {
        String prefix = "type." + type + "." + tool;
        Path source = requireFile(root.resolve(required(manifest, prefix + ".source", Failure.GENERATED_SOURCE_MISSING)),
                Failure.GENERATED_SOURCE_MISSING);
        byte[] bytes = Files.readAllBytes(source);
        requireEquals(required(manifest, prefix + ".sha256", Failure.GENERATED_SOURCE_MISSING), sourceSha256(bytes),
                Failure.ARTIFACT_SHA_MISMATCH, prefix + " SHA");
        requireEquals(required(manifest, prefix + ".signature", Failure.GENERATED_SOURCE_MISSING),
                StructuredSourceParser.semanticSignature(type, bytes), Failure.SEMANTIC_DIVERGENCE, prefix + " signature");
    }

    private static ExtractedSource extract(Path jarPath, String type, Path destinationRoot) throws Exception {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            List<String> matches = jar.stream().map(entry -> entry.getName())
                    .filter(name -> name.endsWith("/" + type + ".java") || name.equals(type + ".java")).toList();
            if (matches.size() != 1) {
                throw new BaselineException(Failure.GENERATED_SOURCE_MISSING,
                        jarPath + " contains " + matches.size() + " entries for " + type);
            }
            String name = matches.getFirst();
            byte[] bytes;
            try (InputStream input = jar.getInputStream(jar.getJarEntry(name))) {
                bytes = input.readAllBytes();
            }
            Path destination = destinationRoot.resolve(name);
            Files.createDirectories(destination.getParent());
            Files.write(destination, bytes);
            return new ExtractedSource(destinationRoot.getParent().relativize(destination).toString().replace('\\', '/'), bytes);
        }
    }

    private static void verifyArtifact(Properties manifest, Path root, String prefix, Failure failure) throws Exception {
        Path artifact = requireFile(root.resolve(required(manifest, prefix + ".path", failure)), failure);
        requireEquals(required(manifest, prefix + ".sha256", failure), sha256(artifact), failure, prefix + " SHA");
    }

    private static Properties load(Path path, Failure failure) throws Exception {
        requireFile(path, failure);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Path requireFile(Path path, Failure failure) {
        if (!Files.isRegularFile(path)) {
            throw new BaselineException(failure, path.toString());
        }
        return path;
    }

    private static String required(Properties properties, String key, Failure failure) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new BaselineException(failure, "missing property " + key);
        }
        return value;
    }

    private static void equal(Properties expected, Properties actual, String key, Failure failure) {
        requireEquals(required(expected, key, failure), required(actual, key, failure), failure, key);
    }

    private static void requireEquals(String expected, String actual, Failure failure, String subject) {
        if (!expected.equals(actual)) {
            throw new BaselineException(failure, subject + ": expected " + expected + " but got " + actual);
        }
    }

    private static void addSource(List<String> manifest, String type, String tool, ExtractedSource source, String signature)
            throws Exception {
        String prefix = "type." + type + "." + tool;
        add(manifest, prefix + ".source", source.relativePath());
        add(manifest, prefix + ".sha256", sourceSha256(source.bytes()));
        add(manifest, prefix + ".signature", signature);
    }

    private static void add(List<String> manifest, String key, String value) {
        manifest.add(key + "=" + value);
    }

    private static void writeManifest(Path path, List<String> lines) throws IOException {
        lines.sort(Comparator.naturalOrder());
        Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    private static void copy(Path source, Path destination) throws IOException {
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    private static String sourceSha256(byte[] bytes) throws Exception {
        return sha256(new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    enum Failure {
        INVALID_ARGUMENTS,
        LOOM_OUTPUT_MISSING,
        MODDEV_OUTPUT_MISSING,
        ORIGIN_MISSING,
        ORIGIN_SHA_MISMATCH,
        GENERATED_MANIFEST_MISSING,
        REFERENCE_MANIFEST_MISSING,
        GENERATED_SOURCE_MISSING,
        REFERENCE_SOURCE_MISSING,
        ARTIFACT_SHA_MISMATCH,
        REFERENCE_MISMATCH,
        SEMANTIC_DIVERGENCE
    }

    static final class BaselineException extends IllegalStateException {
        BaselineException(Failure failure, String detail) {
            super(failure + ": " + detail);
        }
    }

    private record ExtractedSource(String relativePath, byte[] bytes) {}
}
