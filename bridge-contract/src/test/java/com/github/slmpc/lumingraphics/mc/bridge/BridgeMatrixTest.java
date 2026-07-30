package com.github.slmpc.lumingraphics.mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class BridgeMatrixTest {
    private static final Path MATRIX = Path.of("..", "docs", "bridge-matrix.csv");
    private static final Path MANIFEST_2612 = Path.of("..", "reference", "vanilla-26.1.2", "manifest.properties");
    private static final Path MANIFEST_262 = Path.of("..", "reference", "vanilla-26.2", "manifest.properties");
    private static final List<String> EXACT_HEADER = List.of("version", "loader", "backend", "object", "direction",
            "minimum_mode", "accessor", "thread_context_rule", "owner", "invalidation", "close_behavior",
            "test_id", "unsupported_reason", "neoform_origin", "source_origin", "manifest_sha256",
            "generated_sources_sha256", "signature_set_sha256", "origin_artifact", "origin_sha256",
            "delta_profile", "delta_profile_sha256");
    private static final List<String> IMMUTABLE_PROVENANCE = EXACT_HEADER.subList(15, EXACT_HEADER.size());
    private static final String DELTA_PROFILE = "vanilla-26.1.2-to-26.2-v1";

    @Test
    void matrixHasExactSchemaCartesianCoverageAndRules() throws Exception {
        BridgeMatrix matrix = BridgeMatrix.parse(MATRIX);
        assertEquals(168, matrix.rows().size());
        assertEquals(Set.of("MC26.1.2", "26.2"), matrix.values("version"));
        assertEquals(Set.of("Fabric", "NeoForge"), matrix.values("loader"));
        assertEquals(Set.of("opengl", "vulkan", "unknown"), matrix.values("backend"));
        assertEquals(Set.of("texture", "buffer", "shader-module", "sampler", "pipeline", "command-encoder", "render-pass"),
                matrix.values("object"));
        assertEquals(Set.of("MINECRAFT_TO_LUMIN", "LUMIN_TO_MINECRAFT"), matrix.values("direction"));
        assertEquals(168, matrix.rows().stream().map(BridgeMatrix.Row::key).collect(Collectors.toSet()).size());
        assertTrue(matrix.rows().stream().allMatch(row -> !row.testId().isBlank()));
        assertTrue(matrix.rows().stream().filter(row -> row.backend().equals("opengl"))
                .allMatch(row -> !row.threadContextRule().isBlank() && !row.accessor().isBlank()
                        && row.invalidation().contains("token")));
        assertTrue(matrix.rows().stream().filter(row -> !row.backend().equals("opengl"))
                .allMatch(row -> row.minimumMode() == BridgeMode.UNSUPPORTED
                        && !row.unsupportedReason().isBlank()));
        assertTrue(matrix.rows().stream().allMatch(row -> row.owner().equals("BORROWED")
                || row.owner().equals("OWNED") || row.owner().equals("NONE")));
        assertTrue(matrix.rows().stream().allMatch(row -> !row.fields().get("source_origin").isBlank()));
        assertTrue(matrix.rows().stream().filter(row -> row.version().equals("MC26.1.2"))
                .allMatch(row -> row.fields().get("neoform_origin").equals("26.1.2-1")));
        assertTrue(matrix.rows().stream().filter(row -> row.version().equals("26.2"))
                .allMatch(row -> row.fields().get("neoform_origin").equals("26.2-2")));
        System.out.println("ARCH_MC_LEDGER rows=168 schemaColumns=22 versions=2 loaders=2 backends=3 objects=7 directions=2");
    }

    @Test
    void matrixMutationsAreRejected() throws Exception {
        List<String> original = Files.readAllLines(MATRIX);
        List<List<String>> mutations = new ArrayList<>();
        mutations.add(replace(original, "BORROWED_ZERO_COPY", "UNSUPPORTED"));
        mutations.add(replace(original, ",BORROWED,", ",NONE,"));
        mutations.add(replace(original, "bridge-MC26.1.2", ""));
        mutations.add(replace(original, ",opengl,", ",metal,"));
        mutations.add(replace(original, "MC26.1.2", "MC25"));
        mutations.add(removeColumn(original, "neoform_origin"));
        mutations.add(removeColumn(original, "source_origin"));
        mutations.add(replace(original, "neoform_origin", "neoform_origin_renamed"));
        mutations.add(replace(original, "source_origin", "source_origin_renamed"));
        mutations.add(replace(original, "26.1.2-1", "26.1.2-wrong"));
        mutations.add(replace(original, "loom-mojang-sources", "fabricated-source"));
        mutations.add(replace(original, ",26.1.2-1,", ",,"));
        mutations.add(replace(original, ",loom-mojang-sources", ","));
        mutations.add(new ArrayList<>(original));
        mutations.get(13).remove(1);
        mutations.add(new ArrayList<>(original));
        mutations.get(14).add(original.get(1));
        for (int index = 0; index < mutations.size(); index++) {
            Path fixture = Files.createTempFile("bridge-matrix-mutation-" + index, ".csv");
            try {
                Files.write(fixture, mutations.get(index));
                assertThrows(BridgeMatrixException.class, () -> BridgeMatrix.parse(fixture), "mutation " + index);
            } finally {
                Files.deleteIfExists(fixture);
            }
        }
        System.out.println("ARCH_MC_MATRIX_MUTATIONS rejected=15 tempFixtures cleanup=15 schema-count-policy-provenance");
    }

    @Test
    void malformedCsvIsRejectedAndTemporaryFixturesAreCleaned() throws Exception {
        for (String malformed : List.of("version,loader\n\"unterminated", "version,loader\nonly-one-field")) {
            Path fixture = Files.createTempFile("bridge-matrix-malformed", ".csv");
            try {
                Files.writeString(fixture, malformed);
                assertThrows(BridgeMatrixException.class, () -> BridgeMatrix.parse(fixture));
            } finally {
                assertTrue(Files.deleteIfExists(fixture));
                assertFalse(Files.exists(fixture));
            }
        }
    }

    @Test
    void exactOrderedHeaderRejectsUnknownMissingDuplicateAndReorderedColumns() throws Exception {
        List<String> original = Files.readAllLines(MATRIX);
        List<List<String>> mutations = List.of(
                addColumn(original, "unknown", "value"),
                removeColumn(original, "manifest_sha256"),
                duplicateColumn(original, "origin_sha256"),
                swapColumns(original, "manifest_sha256", "generated_sources_sha256"));
        for (int index = 0; index < mutations.size(); index++) {
            assertMutationRejected(mutations.get(index), "header mutation " + index);
        }
    }

    @Test
    void manifestProvenanceAndCrossVersionDeltaAreCanonicalAndExact() throws Exception {
        ManifestFacts first = ManifestFacts.read(MANIFEST_2612);
        ManifestFacts second = ManifestFacts.read(MANIFEST_262);
        assertEquals("f5ba20713963db05c337a3fee26291b6f45728f04259520b2a805cdeb65c9e03",
                first.manifestSha256());
        assertEquals("624ef659693476f765a1ef10d0b28a77df6d9bf73a3634c944bc13b6229fc3a8",
                second.manifestSha256());
        assertEquals("4f7322d60daea68f820c263b3dcabe9600324582906fc30f27d30b400b178c50",
                first.signatureSetSha256());
        assertEquals("f8d72de5a5634ff5177a81575d197e14e5523754b7a4440f87f3cef8da207346",
                second.signatureSetSha256());
        assertEquals(Set.of("CommandEncoder", "GlBuffer", "GlDevice", "GlTexture", "GlTextureView", "GpuBuffer",
                "GpuDevice", "RenderPass", "RenderPipeline", "RenderSystem"), first.signatureChanges(second));
        assertEquals(Set.of("CommandEncoder", "GlBuffer", "GlDevice", "GlProgram", "GlShaderModule", "GlTexture",
                "GlTextureView", "GpuBuffer", "GpuDevice", "GpuTexture", "RenderPass", "RenderPipeline",
                "RenderSystem"), first.sourceHashChanges(second));
        assertFalse(first.signatureChanges(second).contains("GlShaderModule"));
        assertTrue(first.sourceHashChanges(second).contains("GlShaderModule"));
        String deltaCanonical = deltaCanonical(first, second);
        assertEquals("f9dc86cf5fd32eecfcde50418711f002085122219a4258db121de678b3609677",
                sha256(deltaCanonical.getBytes(StandardCharsets.UTF_8)));

        BridgeMatrix matrix = BridgeMatrix.parse(MATRIX);
        for (BridgeMatrix.Row row : matrix.rows()) {
            ManifestFacts facts = row.version().equals("MC26.1.2") ? first : second;
            assertEquals(facts.manifestSha256(), row.fields().get("manifest_sha256"));
            assertEquals(facts.property("generated.sources.sha256"), row.fields().get("generated_sources_sha256"));
            assertEquals(facts.signatureSetSha256(), row.fields().get("signature_set_sha256"));
            assertEquals(facts.property("origin.artifact"), row.fields().get("origin_artifact"));
            assertEquals(facts.property("origin.sha256"), row.fields().get("origin_sha256"));
            assertEquals(DELTA_PROFILE, row.fields().get("delta_profile"));
            assertEquals(sha256(deltaCanonical.getBytes(StandardCharsets.UTF_8)),
                    row.fields().get("delta_profile_sha256"));
        }
    }

    @Test
    void everyImmutableProvenanceFieldRejectsAbsentTamperedStaleAndVersionMismatch() throws Exception {
        List<String> original = Files.readAllLines(MATRIX);
        for (String field : IMMUTABLE_PROVENANCE) {
            assertMutationRejected(removeColumn(original, field), field + " absent");
            assertMutationRejected(mutateCell(original, field, valueAt(original, field) + "x"), field + " tampered");
            assertMutationRejected(mutateCell(original, field, "stale"), field + " stale");
            String otherVersion = valueAtVersion(original, field, "26.2");
            if (otherVersion.equals(valueAt(original, field))) otherVersion = "version-mismatch";
            assertMutationRejected(mutateCell(original, field, otherVersion), field + " version mismatch");
        }
    }

    private static List<String> replace(List<String> source, String before, String after) {
        List<String> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).contains(before)) { result.set(i, result.get(i).replace(before, after)); return result; }
        }
        fail("fixture text not found: " + before);
        return result;
    }

    private static List<String> removeColumn(List<String> source, String columnName) {
        String[] header = source.getFirst().split(",", -1);
        int removed = java.util.Arrays.asList(header).indexOf(columnName);
        assertTrue(removed >= 0, "fixture column not found: " + columnName);
        List<String> result = new ArrayList<>();
        for (String line : source) {
            List<String> fields = new ArrayList<>(java.util.Arrays.asList(line.split(",", -1)));
            fields.remove(removed);
            result.add(String.join(",", fields));
        }
        return result;
    }

    private static List<String> addColumn(List<String> source, String name, String value) {
        List<String> result = new ArrayList<>();
        result.add(source.getFirst() + "," + name);
        for (String line : source.subList(1, source.size())) result.add(line + "," + value);
        return result;
    }

    private static List<String> duplicateColumn(List<String> source, String columnName) {
        int column = columnIndex(source, columnName);
        List<String> result = new ArrayList<>();
        for (String line : source) {
            List<String> fields = new ArrayList<>(Arrays.asList(line.split(",", -1)));
            fields.add(column + 1, fields.get(column));
            result.add(String.join(",", fields));
        }
        return result;
    }

    private static List<String> swapColumns(List<String> source, String first, String second) {
        int firstIndex = columnIndex(source, first);
        int secondIndex = columnIndex(source, second);
        List<String> result = new ArrayList<>();
        for (String line : source) {
            List<String> fields = new ArrayList<>(Arrays.asList(line.split(",", -1)));
            String value = fields.get(firstIndex);
            fields.set(firstIndex, fields.get(secondIndex));
            fields.set(secondIndex, value);
            result.add(String.join(",", fields));
        }
        return result;
    }

    private static List<String> mutateCell(List<String> source, String columnName, String value) {
        int column = columnIndex(source, columnName);
        List<String> result = new ArrayList<>(source);
        List<String> fields = new ArrayList<>(Arrays.asList(result.get(1).split(",", -1)));
        fields.set(column, value);
        result.set(1, String.join(",", fields));
        return result;
    }

    private static String valueAt(List<String> source, String columnName) {
        return source.get(1).split(",", -1)[columnIndex(source, columnName)];
    }

    private static String valueAtVersion(List<String> source, String columnName, String version) {
        int column = columnIndex(source, columnName);
        int versionColumn = columnIndex(source, "version");
        return source.stream().skip(1).map(line -> line.split(",", -1))
                .filter(fields -> fields[versionColumn].equals(version)).findFirst().orElseThrow()[column];
    }

    private static int columnIndex(List<String> source, String columnName) {
        int index = Arrays.asList(source.getFirst().split(",", -1)).indexOf(columnName);
        assertTrue(index >= 0, "fixture column not found: " + columnName);
        return index;
    }

    private static void assertMutationRejected(List<String> lines, String description) throws Exception {
        Path fixture = Files.createTempFile("bridge-matrix-provenance", ".csv");
        try {
            Files.write(fixture, lines);
            assertThrows(BridgeMatrixException.class, () -> BridgeMatrix.parse(fixture), description);
        } finally {
            assertTrue(Files.deleteIfExists(fixture));
        }
    }

    private static String deltaCanonical(ManifestFacts first, ManifestFacts second) {
        return "from=" + first.property("minecraft.version") + "\n"
                + "to=" + second.property("minecraft.version") + "\n"
                + "signature.changed=" + String.join(",", first.signatureChanges(second).stream().sorted().toList()) + "\n"
                + "source_hash.changed=" + String.join(",", first.sourceHashChanges(second).stream().sorted().toList()) + "\n";
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ManifestFacts(Map<String, String> properties, String manifestSha256,
                                 String signatureSetSha256) {
        private static ManifestFacts read(Path path) throws Exception {
            Map<String, String> properties = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('=');
                if (separator > 0) properties.put(line.substring(0, separator), line.substring(separator + 1));
            }
            String signatureCanonical = properties.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("type.") && entry.getKey().endsWith(".signature"))
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue() + "\n")
                    .collect(Collectors.joining());
            return new ManifestFacts(Map.copyOf(properties), sha256(Files.readAllBytes(path)),
                    sha256(signatureCanonical.getBytes(StandardCharsets.UTF_8)));
        }

        private String property(String key) { return properties.get(key); }
        private Set<String> signatureChanges(ManifestFacts other) { return changes(other, ".signature"); }
        private Set<String> sourceHashChanges(ManifestFacts other) { return changes(other, ".sha256"); }
        private Set<String> changes(ManifestFacts other, String suffix) {
            return properties.keySet().stream().filter(key -> key.startsWith("type.") && key.endsWith(suffix))
                    .filter(key -> !properties.get(key).equals(other.properties.get(key)))
                    .map(key -> key.substring("type.".length(), key.length() - suffix.length()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
