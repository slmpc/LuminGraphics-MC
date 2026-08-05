package com.github.slmpc.lumingraphics.mc.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

final class ArtifactCatalog {
    static final String LUMIN_VERSION = "1.2.0";
    static final String MC_VERSION = "1.2.0";
    static final String PRISM_VERSION = "0.2.0";
    static final List<Coordinate> EXPECTED = List.of(
            new Coordinate("com.github.slmpc.lumingraphics.mc", "bridge-contract", MC_VERSION),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-core", LUMIN_VERSION),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-render", LUMIN_VERSION),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-text", LUMIN_VERSION),
            new Coordinate("com.github.slmpc.lumingraphics", "lumin-graphics-ui", LUMIN_VERSION),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-core", PRISM_VERSION),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl-common", PRISM_VERSION),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl41", PRISM_VERSION),
            new Coordinate("com.github.slmpc.prismrhi", "prism-rhi-backend-opengl46", PRISM_VERSION));

    private ArtifactCatalog() {}

    static LinkedHashMap<String, Artifact> loadResolved(List<Path> resolvedJars) throws IOException {
        if (resolvedJars.size() != EXPECTED.size()) {
            throw new IOException("Expected " + EXPECTED.size() + " resolved artifacts, got " + resolvedJars.size());
        }
        LinkedHashMap<String, Artifact> artifacts = new LinkedHashMap<>();
        for (Coordinate coordinate : EXPECTED) {
            Artifact artifact = loadResolved(resolvedJars, coordinate);
            if (artifacts.put(coordinate.artifact(), artifact) != null) {
                throw new IOException("Duplicate resolved artifact: " + coordinate.artifact());
            }
        }
        return artifacts;
    }

    private static Artifact loadResolved(List<Path> resolvedJars, Coordinate coordinate) throws IOException {
        String baseName = coordinate.artifact() + '-' + coordinate.version();
        Path jar = resolvedJars.stream()
                .filter(path -> path.getFileName().toString().equals(baseName + ".jar"))
                .findFirst()
                .orElseThrow(() -> new IOException("Resolved artifact is missing: " + coordinate));
        if (!Files.isRegularFile(jar)) {
            throw new IOException("Resolved artifact is not a file: " + jar);
        }
        byte[] bytes = Files.readAllBytes(jar);
        return new Artifact(coordinate, jar, bytes, ArchiveContents.sha256(bytes));
    }

    record Coordinate(String group, String artifact, String version) {
        String id() {
            return group + ':' + artifact + ':' + version;
        }
    }

    record Artifact(Coordinate coordinate, Path jar, byte[] bytes, String sha256) {}
}
