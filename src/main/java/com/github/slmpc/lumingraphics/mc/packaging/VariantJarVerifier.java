package com.github.slmpc.lumingraphics.mc.packaging;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class VariantJarVerifier {
    private VariantJarVerifier() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: VariantJarVerifier <project-root> <isolated-m2> [artifact-override-dir]");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path repository = Path.of(args[1]).toAbsolutePath().normalize();
        Path override = args.length == 3 ? Path.of(args[2]).toAbsolutePath().normalize() : null;
        if (!Files.isDirectory(repository)) {
            throw new IOException("Isolated Maven repository does not exist: " + repository);
        }
        Map<String, ArtifactCatalog.Artifact> artifacts = new LinkedHashMap<>();
        for (ArtifactCatalog.Coordinate coordinate : ArtifactCatalog.EXPECTED) {
            ArtifactCatalog.Artifact artifact = ArtifactCatalog.load(repository, coordinate);
            artifacts.put(coordinate.artifact(), artifact);
            System.out.printf("PROVENANCE %s sha256=%s path=%s%n", coordinate.id(), artifact.sha256(), artifact.jar());
        }
        for (Variant variant : variants(root, override)) {
            verifyVariant(variant, artifacts);
        }
        System.out.println("VARIANT_MATRIX_OK variants=4 nestedArtifacts=32 version=0.1.0");
    }

    private static List<Variant> variants(Path root, Path override) {
        return List.of(
                variant(root, override, "fabric", "26.1.2", "2612", "v2612"),
                variant(root, override, "neoforge", "26.1.2", "2612", "v2612"),
                variant(root, override, "fabric", "26.2", "262", "v262"),
                variant(root, override, "neoforge", "26.2", "262", "v262"));
    }

    private static Variant variant(Path root, Path override, String loader, String minecraft, String key, String packageKey) {
        String fileName = "lumin-graphics-mc-" + loader + '-' + minecraft + "-0.1.0.jar";
        Path standard = root.resolve("mc-" + minecraft + '-' + loader).resolve("build/libs").resolve(fileName);
        Path artifact = override == null ? standard : override.resolve(fileName);
        String entrypoint = loader.equals("fabric")
                ? "com/github/slmpc/lumingraphics/mc/fabric/v" + key + "/LuminGraphicsFabricClient.class"
                : "com/github/slmpc/lumingraphics/mc/" + packageKey + "/neoforge/LuminGraphicsNeoForge" + key + ".class";
        String common = "com/github/slmpc/lumingraphics/mc/" + packageKey + "/bridge/Blaze3DBridge" + key + ".class";
        String mixin = minecraft.equals("26.1.2") ? "lumin_graphics_mc_2612.mixins.json" : "lumin-graphics-mc-262.mixins.json";
        return new Variant(loader, minecraft, artifact, entrypoint, common, mixin);
    }

    private static void verifyVariant(Variant variant, Map<String, ArtifactCatalog.Artifact> artifacts) throws Exception {
        if (!Files.isRegularFile(variant.path())) {
            throw new IOException("Final variant artifact is missing: " + variant.path());
        }
        byte[] outerBytes = Files.readAllBytes(variant.path());
        ArchiveContents outer = ArchiveContents.read(outerBytes, variant.path().toString());
        requireEntries(outer, variant);
        Map<String, byte[]> nested = variant.loader().equals("fabric")
                ? fabricNested(outer, variant) : neoForgeNested(outer, variant);
        if (!nested.keySet().equals(artifacts.keySet())) {
            throw new IOException("Nested artifact set mismatch in " + variant.path() + ": " + nested.keySet());
        }
        Map<String, String> effectiveOwners = new HashMap<>();
        addEffectiveEntries(effectiveOwners, outer, variant.path().getFileName().toString(), true);
        int fonts = 0;
        int shaders = 0;
        int classes = 0;
        for (Map.Entry<String, byte[]> nestedEntry : nested.entrySet()) {
            ArtifactCatalog.Artifact source = artifacts.get(nestedEntry.getKey());
            ArchiveContents packaged = ArchiveContents.read(nestedEntry.getValue(), nestedEntry.getKey());
            if (variant.loader().equals("fabric")) {
                packaged.verifyFabricWrapper(ArchiveContents.read(source.bytes(), source.jar().toString()), nestedEntry.getKey());
            } else if (!source.sha256().equals(ArchiveContents.sha256(nestedEntry.getValue()))) {
                throw new IOException("NeoForge jarJar hash differs from isolated source for " + nestedEntry.getKey());
            }
            rejectForbidden(packaged, nestedEntry.getKey());
            addEffectiveEntries(effectiveOwners, packaged, nestedEntry.getKey(), false);
            for (String name : packaged.names()) {
                if (name.endsWith(".ttf")) fonts++;
                if (name.endsWith(".glsl") || name.endsWith(".spv")) shaders++;
                if (name.endsWith(".class")) classes++;
            }
            System.out.printf("NESTED %s %s bytes=%d sourceSha256=%s packagedSha256=%s%n",
                    variant.path().getFileName(), source.coordinate().id(), nestedEntry.getValue().length,
                    source.sha256(), ArchiveContents.sha256(nestedEntry.getValue()));
        }
        if (fonts == 0 || shaders == 0 || classes == 0) {
            throw new IOException("Required Lumin/Prism payload is incomplete in " + variant.path());
        }
        System.out.printf("VARIANT_OK loader=%s minecraft=%s file=%s bytes=%d sha256=%s nested=8 classes=%d fonts=%d shaders=%d%n",
                variant.loader(), variant.minecraft(), variant.path(), outerBytes.length,
                ArchiveContents.sha256(outerBytes), classes, fonts, shaders);
    }

    private static void requireEntries(ArchiveContents outer, Variant variant) throws IOException {
        List<String> required = new ArrayList<>(List.of(variant.entrypoint(), variant.commonBridge(), variant.mixin()));
        if (variant.loader().equals("fabric")) {
            required.add("fabric.mod.json");
            required.add("lumin_graphics_mc_" + variant.minecraft().replace(".", "") + ".accesswidener");
            JsonObject metadata = parseJson(outer.required("fabric.mod.json"));
            requireText(metadata, "id", "lumin_graphics_mc", variant.path());
            requireText(metadata, "version", "0.1.0+mc" + variant.minecraft(), variant.path());
        } else {
            required.add("META-INF/neoforge.mods.toml");
            required.add("META-INF/accesstransformer.cfg");
            verifyNeoForgeToml(outer.required("META-INF/neoforge.mods.toml"), variant);
        }
        for (String name : required) {
            outer.required(name);
        }
        rejectForbidden(outer, variant.path().toString());
    }

    private static Map<String, byte[]> fabricNested(ArchiveContents outer, Variant variant) throws IOException {
        JsonObject metadata = parseJson(outer.required("fabric.mod.json"));
        JsonArray jars = metadata.getAsJsonArray("jars");
        if (jars == null || jars.size() != ArtifactCatalog.EXPECTED.size()) {
            throw new IOException("Fabric JIJ metadata must contain exactly eight jars: " + variant.path());
        }
        Map<String, byte[]> nested = new TreeMap<>();
        for (JsonElement element : jars) {
            String path = element.getAsJsonObject().get("file").getAsString();
            addNested(nested, artifactName(path), outer.required(path), variant.path());
        }
        return nested;
    }

    private static Map<String, byte[]> neoForgeNested(ArchiveContents outer, Variant variant) throws IOException {
        JsonObject metadata = parseJson(outer.required("META-INF/jarjar/metadata.json"));
        JsonArray jars = metadata.getAsJsonArray("jars");
        if (jars == null || jars.size() != ArtifactCatalog.EXPECTED.size()) {
            throw new IOException("NeoForge jarJar metadata must contain exactly eight jars: " + variant.path());
        }
        Map<String, byte[]> nested = new TreeMap<>();
        for (JsonElement element : jars) {
            JsonObject item = element.getAsJsonObject();
            JsonObject identifier = item.getAsJsonObject("identifier");
            JsonObject version = item.getAsJsonObject("version");
            String artifact = identifier.get("artifact").getAsString();
            ArtifactCatalog.Coordinate expected = ArtifactCatalog.EXPECTED.stream()
                    .filter(value -> value.artifact().equals(artifact)).findFirst()
                    .orElseThrow(() -> new IOException("Unexpected jarJar artifact: " + artifact));
            requireText(identifier, "group", expected.group(), variant.path());
            requireText(version, "artifactVersion", ArtifactCatalog.VERSION, variant.path());
            requireText(version, "range", "[0.1.0]", variant.path());
            String path = item.get("path").getAsString();
            if (!path.endsWith('/' + artifact + '-' + ArtifactCatalog.VERSION + ".jar")) {
                throw new IOException("jarJar path/version mismatch in " + variant.path() + ": " + path);
            }
            addNested(nested, artifact, outer.required(path), variant.path());
        }
        return nested;
    }

    private static void verifyNeoForgeToml(byte[] bytes, Variant variant) throws IOException {
        TomlParseResult result = Toml.parse(new String(bytes, StandardCharsets.UTF_8));
        if (result.hasErrors()) {
            throw new IOException("Invalid NeoForge TOML in " + variant.path() + ": " + result.errors());
        }
        TomlArray mods = result.getArray("mods");
        if (mods == null || mods.size() != 1 || !(mods.get(0) instanceof TomlTable mod)) {
            throw new IOException("NeoForge TOML must define exactly one mod: " + variant.path());
        }
        if (!"lumin_graphics_mc".equals(mod.getString("modId"))
                || !("0.1.0+mc" + variant.minecraft()).equals(mod.getString("version"))) {
            throw new IOException("NeoForge mod id/version mismatch: " + variant.path());
        }
    }

    private static void addNested(Map<String, byte[]> nested, String artifact, byte[] bytes, Path source) throws IOException {
        if (nested.putIfAbsent(artifact, bytes) != null) {
            throw new IOException("Duplicate nested artifact in " + source + ": " + artifact);
        }
    }

    private static String artifactName(String path) throws IOException {
        String fileName = Path.of(path).getFileName().toString();
        String suffix = '-' + ArtifactCatalog.VERSION + ".jar";
        if (!fileName.endsWith(suffix)) {
            throw new IOException("Nested artifact does not use exact 0.1.0: " + path);
        }
        return fileName.substring(0, fileName.length() - suffix.length());
    }

    private static void rejectForbidden(ArchiveContents archive, String source) throws IOException {
        for (String name : archive.names()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            boolean shaderc = lower.contains("prism-rhi-shaderc") || lower.contains("org/lwjgl/util/shaderc/")
                    || lower.startsWith("shaderc/");
            if (lower.startsWith("meta-inf/services/") || shaderc
                    || lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) {
                throw new IOException("Forbidden packaged entry in " + source + ": " + name);
            }
            if (lower.endsWith(".jar") && !lower.startsWith("meta-inf/jars/")
                    && !lower.startsWith("meta-inf/jarjar/")) {
                throw new IOException("Unregistered nested JAR in " + source + ": " + name);
            }
        }
    }

    private static void addEffectiveEntries(Map<String, String> owners, ArchiveContents archive,
                                            String owner, boolean outer) throws IOException {
        for (String name : archive.names()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith("/") || lower.startsWith("meta-inf/") || lower.equals("fabric.mod.json")
                    || (outer && lower.endsWith(".jar"))) {
                continue;
            }
            String previous = owners.putIfAbsent(name, owner);
            if (previous != null) {
                throw new IOException("Duplicate effective path " + name + " in " + previous + " and " + owner);
            }
        }
    }

    private static JsonObject parseJson(byte[] bytes) throws IOException {
        try {
            return JsonParser.parseReader(new StringReader(new String(bytes, StandardCharsets.UTF_8))).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid packaged JSON", exception);
        }
    }

    private static void requireText(JsonObject object, String key, String expected, Path source) throws IOException {
        if (object == null || !object.has(key) || !expected.equals(object.get(key).getAsString())) {
            throw new IOException("Unexpected " + key + " in " + source);
        }
    }

    private record Variant(String loader, String minecraft, Path path, String entrypoint,
                           String commonBridge, String mixin) {}
}
