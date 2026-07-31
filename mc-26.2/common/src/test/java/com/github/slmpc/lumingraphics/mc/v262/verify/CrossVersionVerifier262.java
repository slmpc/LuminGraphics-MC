package com.github.slmpc.lumingraphics.mc.v262.verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CrossVersionVerifier262 {
    private CrossVersionVerifier262() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("expected project and two classpath snapshots");
        Path project = Path.of(args[0]);
        String oldVersion = "26.1" + ".2";
        String oldPackage = "v26" + "12";
        List<String> forbiddenApis = List.of("java.lang." + "reflect", "sun.misc." + "unsafe",
                "jdk.internal." + "misc", "net.fabricmc." + "api", "net.neoforged." + "api");
        List<String> compileClasspath = readClasspathSnapshot(Path.of(args[1]), "compile");
        List<String> runtimeClasspath = readClasspathSnapshot(Path.of(args[2]), "runtime");
        String paths = String.join("\n", compileClasspath) + "\n" + String.join("\n", runtimeClasspath);
        paths = paths.replace('\\', '/').toLowerCase(Locale.ROOT);
        reject(paths.contains("mc-" + oldVersion + "-common") || paths.contains("vanilla-" + oldVersion)
                        || paths.contains("/" + oldPackage + "/"),
                "older version path leaked onto 26.2 classpath");
        List<Path> sources;
        try (var stream = Files.walk(project.resolve("src"))) {
            sources = stream.filter(Files::isRegularFile).toList();
        }
        int scanned = 0;
        for (Path source : sources) {
            String text = Files.readString(source).toLowerCase(Locale.ROOT);
            reject(text.contains("vanilla-" + oldVersion) || text.contains("mc-" + oldVersion + "-common")
                    || text.contains(oldPackage) || text.contains("blaze3dbridge" + oldVersion.replace(".", "")),
                    "older version textual reuse in " + source);
            if (source.toString().endsWith(".java")) {
                reject(forbiddenApis.stream().anyMatch(text::contains),
                        "forbidden reflection/unsafe/loader API in " + source);
            }
            scanned++;
        }
        reject(scanned == 0, "no 26.2 sources inspected");
        System.out.println("VERIFY_NO_CROSS_VERSION_LEAK=PASS scanned=" + scanned);
    }

    private static List<String> readClasspathSnapshot(Path snapshot, String kind) throws Exception {
        require(Files.isRegularFile(snapshot), "missing " + kind + " classpath snapshot " + snapshot);
        List<String> entries = Files.readAllLines(snapshot, StandardCharsets.UTF_8);
        require(!entries.isEmpty(), "empty " + kind + " classpath snapshot " + snapshot);
        Set<String> seen = new HashSet<>();
        for (String entry : entries) {
            require(!entry.isBlank(), "malformed blank entry in " + kind + " classpath snapshot");
            Path path;
            try {
                path = Path.of(entry);
            } catch (InvalidPathException exception) {
                throw new IllegalStateException("malformed entry in " + kind + " classpath snapshot: " + entry,
                        exception);
            }
            require(path.isAbsolute() && path.normalize().toString().equals(entry),
                    "malformed non-absolute or non-normalized entry in " + kind + " classpath snapshot: " + entry);
            String key = entry.replace('\\', '/').toLowerCase(Locale.ROOT);
            require(seen.add(key), "duplicate entry in " + kind + " classpath snapshot: " + entry);
        }
        return entries;
    }

    private static void reject(boolean condition, String message) {
        if (condition) throw new IllegalStateException(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
