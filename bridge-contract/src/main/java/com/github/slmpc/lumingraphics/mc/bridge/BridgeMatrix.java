package com.github.slmpc.lumingraphics.mc.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BridgeMatrix {
    private static final List<String> VERSIONS = List.of("MC26.1.2", "26.2");
    private static final List<String> LOADERS = List.of("Fabric", "NeoForge");
    private static final List<String> BACKENDS = List.of("opengl", "vulkan", "unknown");
    private static final List<String> OBJECTS = List.of("texture", "buffer", "shader-module", "sampler",
            "pipeline", "command-encoder", "render-pass");
    private static final List<String> DIRECTIONS = List.of("MINECRAFT_TO_LUMIN", "LUMIN_TO_MINECRAFT");
    private static final List<String> EXACT_HEADER = List.of("version", "loader", "backend", "object",
            "direction", "minimum_mode", "accessor", "thread_context_rule", "owner", "invalidation",
            "close_behavior", "test_id", "unsupported_reason", "neoform_origin", "source_origin",
            "manifest_sha256", "generated_sources_sha256", "signature_set_sha256", "origin_artifact",
            "origin_sha256", "delta_profile", "delta_profile_sha256");
    private static final String DELTA_PROFILE = "vanilla-26.1.2-to-26.2-v1";
    private static final String DELTA_PROFILE_SHA256 =
            "f9dc86cf5fd32eecfcde50418711f002085122219a4258db121de678b3609677";
    private static final Map<String, Provenance> PROVENANCE = Map.of(
            "MC26.1.2", new Provenance(
                    "f5ba20713963db05c337a3fee26291b6f45728f04259520b2a805cdeb65c9e03",
                    "a85a9b77f4946f753ed64867492749bd8b21247e86682caddd1ae43ed12a49c2",
                    "4f7322d60daea68f820c263b3dcabe9600324582906fc30f27d30b400b178c50",
                    "net.neoforged:neoform:26.1.2-1@zip",
                    "3eb9f8cc282badfbc210a27a8304f14cc58fa40401a94072874dfaded3c8cb52"),
            "26.2", new Provenance(
                    "624ef659693476f765a1ef10d0b28a77df6d9bf73a3634c944bc13b6229fc3a8",
                    "5553dd93ddc852fefab2349aae509ba3e65cbc78f0c35945822767efd8774b32",
                    "f8d72de5a5634ff5177a81575d197e14e5523754b7a4440f87f3cef8da207346",
                    "net.neoforged:neoform:26.2-2@zip",
                    "09fd01e2371a94c78bc4d0d28645ab88206fcef395795182cee841765ebd5a63"));
    private final List<Row> rows;

    private BridgeMatrix(List<Row> rows) { this.rows = List.copyOf(rows); }

    public static BridgeMatrix parse(Path path) {
        final String text;
        try { text = Files.readString(path); }
        catch (IOException error) { throw new BridgeMatrixException("Cannot read matrix: " + path, error); }
        List<List<String>> records = parseCsv(text);
        if (records.isEmpty()) throw new BridgeMatrixException("Matrix is empty");
        List<String> header = records.getFirst();
        if (!header.equals(EXACT_HEADER)) {
            throw new BridgeMatrixException("Invalid matrix schema: " + header);
        }
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) columns.put(header.get(index), index);
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> values = records.get(index);
            if (values.size() != header.size()) throw new BridgeMatrixException("Row " + (index + 1) + " width mismatch");
            Map<String, String> fields = new LinkedHashMap<>();
            for (int column = 0; column < header.size(); column++) fields.put(header.get(column), values.get(column));
            rows.add(Row.from(fields, index + 1));
        }
        validate(rows);
        return new BridgeMatrix(rows);
    }

    public List<Row> rows() { return rows; }
    public Set<String> values(String column) {
        return rows.stream().map(row -> row.fields().get(column)).collect(Collectors.toUnmodifiableSet());
    }

    private static void validate(List<Row> rows) {
        Set<String> expected = new HashSet<>();
        for (String version : VERSIONS) for (String loader : LOADERS) for (String backend : BACKENDS)
            for (String object : OBJECTS) for (String direction : DIRECTIONS)
                expected.add(String.join("|", version, loader, backend, object, direction));
        Set<String> actual = new HashSet<>();
        for (Row row : rows) {
            if (!actual.add(row.key())) throw new BridgeMatrixException("Duplicate key: " + row.key());
            if (!expected.contains(row.key())) throw new BridgeMatrixException("Unexpected key: " + row.key());
            validatePolicy(row);
        }
        if (!actual.equals(expected)) throw new BridgeMatrixException("Cartesian coverage mismatch; expected "
                + expected.size() + ", got " + actual.size());
    }

    private static void validatePolicy(Row row) {
        BridgeMode expectedMode;
        String expectedOwner;
        String expectedReason;
        if (!row.backend().equals("opengl")) {
            expectedMode = BridgeMode.UNSUPPORTED;
            expectedOwner = "NONE";
            expectedReason = row.backend().equals("vulkan") ? "ZERO_COPY_UNSAFE" : "BACKEND_MISMATCH";
        } else if (Set.of("texture", "buffer", "shader-module").contains(row.object())) {
            expectedMode = BridgeMode.BORROWED_ZERO_COPY; expectedOwner = "BORROWED"; expectedReason = "";
        } else if (Set.of("sampler", "pipeline").contains(row.object())) {
            expectedMode = BridgeMode.REBUILT; expectedOwner = "OWNED"; expectedReason = "";
        } else {
            expectedMode = BridgeMode.IN_PLACE_ADAPTER; expectedOwner = "BORROWED"; expectedReason = "";
        }
        if (row.minimumMode() != expectedMode || !row.owner().equals(expectedOwner)
                || !row.unsupportedReason().equals(expectedReason)) {
            throw new BridgeMatrixException("Policy mismatch: " + row.key());
        }
        String canonicalTestId = "bridge-" + row.version() + "-" + row.loader() + "-" + row.backend()
                + "-" + row.object() + "-" + row.direction();
        if (!row.testId().equals(canonicalTestId)) throw new BridgeMatrixException("Noncanonical test_id: " + row.key());
        if (row.accessor().isBlank() || row.threadContextRule().isBlank() || row.invalidation().isBlank()
                || row.closeBehavior().isBlank()) throw new BridgeMatrixException("Blank policy field: " + row.key());
        String expectedNeoForm = row.version().equals("MC26.1.2") ? "26.1.2-1" : "26.2-2";
        String expectedSource = row.loader().equals("Fabric")
                ? "loom-mojang-sources" : "neoform-official-mappings";
        if (!row.neoformOrigin().equals(expectedNeoForm) || !row.sourceOrigin().equals(expectedSource)) {
            throw new BridgeMatrixException("Provenance mismatch: " + row.key());
        }
        Provenance provenance = PROVENANCE.get(row.version());
        if (!row.manifestSha256().equals(provenance.manifestSha256())
                || !row.generatedSourcesSha256().equals(provenance.generatedSourcesSha256())
                || !row.signatureSetSha256().equals(provenance.signatureSetSha256())
                || !row.originArtifact().equals(provenance.originArtifact())
                || !row.originSha256().equals(provenance.originSha256())
                || !row.deltaProfile().equals(DELTA_PROFILE)
                || !row.deltaProfileSha256().equals(DELTA_PROFILE_SHA256)) {
            throw new BridgeMatrixException("Immutable provenance mismatch: " + row.key());
        }
    }

    private static List<List<String>> parseCsv(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character == '"' && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"'); index++;
                } else if (character == '"') quoted = false;
                else field.append(character);
            } else if (character == '"' && field.isEmpty()) quoted = true;
            else if (character == ',') { row.add(field.toString()); field.setLength(0); }
            else if (character == '\n') {
                row.add(stripCarriageReturn(field));
                records.add(List.copyOf(row));
                row.clear();
                field.setLength(0);
            }
            else field.append(character);
        }
        if (quoted) throw new BridgeMatrixException("Unterminated quoted CSV field");
        if (!field.isEmpty() || !row.isEmpty()) { row.add(stripCarriageReturn(field)); records.add(List.copyOf(row)); }
        return records;
    }

    private static String stripCarriageReturn(StringBuilder field) {
        int length = field.length();
        if (length > 0 && field.charAt(length - 1) == '\r') field.setLength(length - 1);
        return field.toString();
    }

    public record Row(Map<String, String> fields, BridgeMode minimumMode) {
        private static Row from(Map<String, String> fields, int line) {
            try { return new Row(Map.copyOf(fields), BridgeMode.valueOf(fields.get("minimum_mode"))); }
            catch (RuntimeException error) { throw new BridgeMatrixException("Invalid row " + line, error); }
        }
        public String version() { return fields.get("version"); }
        public String loader() { return fields.get("loader"); }
        public String backend() { return fields.get("backend"); }
        public String object() { return fields.get("object"); }
        public String direction() { return fields.get("direction"); }
        public String accessor() { return fields.get("accessor"); }
        public String threadContextRule() { return fields.get("thread_context_rule"); }
        public String owner() { return fields.get("owner"); }
        public String invalidation() { return fields.get("invalidation"); }
        public String closeBehavior() { return fields.get("close_behavior"); }
        public String testId() { return fields.get("test_id"); }
        public String unsupportedReason() { return fields.get("unsupported_reason"); }
        public String neoformOrigin() { return fields.get("neoform_origin"); }
        public String sourceOrigin() { return fields.get("source_origin"); }
        public String manifestSha256() { return fields.get("manifest_sha256"); }
        public String generatedSourcesSha256() { return fields.get("generated_sources_sha256"); }
        public String signatureSetSha256() { return fields.get("signature_set_sha256"); }
        public String originArtifact() { return fields.get("origin_artifact"); }
        public String originSha256() { return fields.get("origin_sha256"); }
        public String deltaProfile() { return fields.get("delta_profile"); }
        public String deltaProfileSha256() { return fields.get("delta_profile_sha256"); }
        public String key() { return String.join("|", version(), loader(), backend(), object(), direction()); }
    }

    /*
     * Canonical hashes use lowercase SHA-256. Manifest hashes cover exact bytes. Signature-set input is
     * ordinal-sorted `type.*.signature=value\n`; the shared delta input is `from`, `to`, then ordinal-sorted
     * comma-joined `signature.changed` and `source_hash.changed` lines, all encoded as UTF-8 with LF.
     */
    private record Provenance(String manifestSha256, String generatedSourcesSha256, String signatureSetSha256,
                              String originArtifact, String originSha256) { }
}
