package com.github.slmpc.lumingraphics.mc.packaging;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class VariantJarVerifier {
    private VariantJarVerifier() {}

    public static void main(String[] args) throws Exception {
        int expectedArguments = ArtifactCatalog.EXPECTED.size() + 1;
        if (args.length != expectedArguments && args.length != expectedArguments + 1) {
            throw new IllegalArgumentException("Usage: VariantJarVerifier <project-root> <resolved-artifact>...");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> resolvedJars = new ArrayList<>();
        for (int index = 1; index < expectedArguments; index++) {
            resolvedJars.add(Path.of(args[index]).toAbsolutePath().normalize());
        }
        Path override = args.length == expectedArguments + 1
                ? Path.of(args[expectedArguments]).toAbsolutePath().normalize() : null;
        Map<String, ArtifactCatalog.Artifact> artifacts = ArtifactCatalog.loadResolved(resolvedJars);
        for (ArtifactCatalog.Artifact artifact : artifacts.values()) {
            ArtifactCatalog.Coordinate coordinate = artifact.coordinate();
            System.out.printf("PROVENANCE %s sha256=%s path=%s%n", coordinate.id(), artifact.sha256(), artifact.jar());
        }
        for (Variant variant : variants(root, override)) {
            verifyVariant(variant, artifacts);
        }
        System.out.println("VARIANT_MATRIX_OK variants=4 shadowedArtifacts=36 version=1.2.5-SNAPSHOT");
    }

    private static List<Variant> variants(Path root, Path override) {
        return List.of(
                variant(root, override, "fabric", "26.1.2", "2612", "v2612"),
                variant(root, override, "neoforge", "26.1.2", "2612", "v2612"),
                variant(root, override, "fabric", "26.2", "262", "v262"),
                variant(root, override, "neoforge", "26.2", "262", "v262"));
    }

    private static Variant variant(Path root, Path override, String loader, String minecraft, String key, String packageKey) {
        String fileName = "lumin-graphics-mc-" + loader + '-' + minecraft + "-1.2.5-SNAPSHOT.jar";
        Path standard = standardArtifactPath(root, loader, minecraft);
        Path artifact = override == null ? standard : override.resolve(fileName);
        String entrypoint = loader.equals("fabric")
                ? "com/github/slmpc/lumingraphics/mc/fabric/v" + key + "/LuminGraphicsFabricClient.class"
                : "com/github/slmpc/lumingraphics/mc/" + packageKey + "/neoforge/LuminGraphicsNeoForge" + key + ".class";
        String common = "com/github/slmpc/lumingraphics/mc/" + packageKey + "/bridge/Blaze3DBridge" + key + ".class";
        String mixin = minecraft.equals("26.1.2") ? "lumin_graphics_mc_2612.mixins.json" : "lumin-graphics-mc-262.mixins.json";
        return new Variant(loader, minecraft, artifact, entrypoint, common, mixin);
    }

    static Path standardArtifactPath(Path root, String loader, String minecraft) {
        String fileName = "lumin-graphics-mc-" + loader + '-' + minecraft + "-1.2.5-SNAPSHOT.jar";
        return root.resolve("mc-" + minecraft).resolve(loader).resolve("build/libs").resolve(fileName);
    }

    private static void verifyVariant(Variant variant, Map<String, ArtifactCatalog.Artifact> artifacts) throws Exception {
        requireFinalArtifact(variant.path());
        byte[] outerBytes = Files.readAllBytes(variant.path());
        ArchiveContents outer = ArchiveContents.read(outerBytes, variant.path().toString());
        requireEntries(outer, variant);
        Map<String, String> effectiveOwners = new HashMap<>();
        int shaders = 0;
        int classes = 0;
        for (ArtifactCatalog.Artifact source : artifacts.values()) {
            ArchiveContents sourceArchive = ArchiveContents.read(source.bytes(), source.jar().toString());
            rejectForbidden(sourceArchive, source.coordinate().id());
            requireShadowedEntries(outer, sourceArchive, source.coordinate().id(), variant.path());
            addEffectiveEntries(effectiveOwners, sourceArchive, source.coordinate().id());
            for (String name : sourceArchive.names()) {
                if (name.endsWith(".glsl") || name.endsWith(".spv")) shaders++;
                if (name.endsWith(".class")) classes++;
            }
            System.out.printf("SHADOWED %s %s sourceSha256=%s%n",
                    variant.path().getFileName(), source.coordinate().id(), source.sha256());
        }
        if (shaders == 0 || classes == 0) {
            throw new IOException("Required Lumin/Prism payload is incomplete in " + variant.path());
        }
        System.out.printf("VARIANT_OK loader=%s minecraft=%s file=%s bytes=%d sha256=%s shadowed=8 classes=%d shaders=%d%n",
                variant.loader(), variant.minecraft(), variant.path(), outerBytes.length,
                ArchiveContents.sha256(outerBytes), classes, shaders);
    }

    static void requireFinalArtifact(Path artifact) throws IOException {
        if (!Files.isRegularFile(artifact)) {
            throw new IOException("Final variant artifact is missing: " + artifact);
        }
    }

    private static void requireEntries(ArchiveContents outer, Variant variant) throws IOException {
        List<String> required = new ArrayList<>(List.of(variant.entrypoint(), variant.commonBridge(), variant.mixin()));
        required.add("com/github/slmpc/lumingraphics/mc/bridge/BridgeResult.class");
        if (variant.minecraft().equals("26.1.2")) {
            required.add("com/github/slmpc/lumingraphics/mc/v2612/runtime/MinecraftGraphicsRuntime2612.class");
        }
        if (variant.loader().equals("fabric")) {
            required.add("fabric.mod.json");
            required.add("lumin_graphics_mc_" + variant.minecraft().replace(".", "") + ".accesswidener");
            JsonObject metadata = parseJson(outer.required("fabric.mod.json"));
            requireText(metadata, "id", "lumin_graphics_mc", variant.path());
            requireText(metadata, "version", "1.2.5-SNAPSHOT+mc" + variant.minecraft(), variant.path());
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
                || !("1.2.5-SNAPSHOT+mc" + variant.minecraft()).equals(mod.getString("version"))) {
            throw new IOException("NeoForge mod id/version mismatch: " + variant.path());
        }
    }

    private static void requireShadowedEntries(ArchiveContents outer, ArchiveContents source,
                                               String coordinate, Path destination) throws IOException {
        for (String name : source.names()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith("/") || lower.startsWith("meta-inf/")) {
                continue;
            }
            byte[] expected = source.required(name);
            byte[] actual = outer.required(name);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IOException("Shadowed entry differs from " + coordinate + " in " + destination + ": " + name);
            }
        }
    }

    private static void rejectForbidden(ArchiveContents archive, String source) throws IOException {
        for (String name : archive.names()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            boolean shaderc = lower.contains("prism-rhi-shaderc") || lower.contains("org/lwjgl/util/shaderc/")
                    || lower.startsWith("shaderc/");
            if (lower.startsWith("meta-inf/services/") || shaderc
                    || lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")
                    || lower.endsWith(".ttf") || lower.endsWith(".otf")) {
                throw new IOException("Forbidden packaged entry in " + source + ": " + name);
            }
            if (lower.endsWith(".jar")) {
                throw new IOException("Nested JAR is forbidden in direct-shadow artifact " + source + ": " + name);
            }
        }
    }

    private static void addEffectiveEntries(Map<String, String> owners, ArchiveContents archive,
                                            String owner) throws IOException {
        for (String name : archive.names()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith("/") || lower.startsWith("meta-inf/") || lower.equals("fabric.mod.json")) {
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
