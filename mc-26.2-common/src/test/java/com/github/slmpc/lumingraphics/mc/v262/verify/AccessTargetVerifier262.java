package com.github.slmpc.lumingraphics.mc.v262.verify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipFile;

public final class AccessTargetVerifier262 {
    private AccessTargetVerifier262() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("expected reference, targets, classes, generated source jar");
        verify(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        System.out.println("VERIFY_262_ACCESS_TARGETS=PASS targets=23 bytecode_hooks=11");
    }

    public static void verify(Path reference, Path targetFile, Path classes, Path generatedJar) throws Exception {
        Properties targets = new Properties();
        try (InputStream input = Files.newInputStream(targetFile)) { targets.load(input); }
        require("26.2".equals(targets.getProperty("minecraft.version")), "wrong target version");
        require("26.2-2".equals(targets.getProperty("neoform.origin")), "wrong NeoForm origin");
        List<String> names = List.of("GlTexture", "GlTextureView", "GlBuffer", "GlBufferDsa", "GlShaderModule", "GlProgram",
                "GlProgramLink", "GpuTextureFormat", "GpuBufferMap", "CommandEncoder", "CommandEncoderSubmit",
                "CommandEncoderTransient", "CommandEncoderPassState", "RenderPass", "RenderPassVertexSlice",
                "RenderPipelineShape", "RenderPipelineBindings", "RenderPipelineTopology", "GpuDevice",
                "GpuDeviceSurface", "Font", "TextRenderable", "BakedGlyph");
        try (ZipFile jar = new ZipFile(generatedJar.toFile())) {
            for (String name : names) {
                String sourcePath = targets.getProperty(name + ".source");
                String expected = normalize(targets.getProperty(name + ".target"));
                String source = readSource(reference, jar, sourcePath);
                String normalizedSource = normalize(source);
                require(normalizedSource.contains(expected), "missing/drifted 26.2 target " + name + ": " + expected);
            }
        }
        List<String> hooks = List.of(
                "mixin/GlInvokers262$Texture.class", "mixin/GlInvokers262$TextureView.class",
                "mixin/GlInvokers262$Buffer.class", "mixin/GlInvokers262$Shader.class",
                "mixin/GlInvokers262$Program.class", "mixin/GlTextureBorrowedMixin262.class",
                "mixin/GlBufferBorrowedMixin262.class", "mixin/GlShaderBorrowedMixin262.class",
                "mixin/GlProgramBorrowedMixin262.class", "access/GlBufferDsaAccess262.class",
                "mixin/GlBufferDsaMixin262.class");
        Path root = classes.resolve("com/github/slmpc/lumingraphics/mc/v262");
        for (String hook : hooks) require(Files.size(root.resolve(hook)) > 0, "missing compiled hook " + hook);
        scanBytecode(root.resolve("mixin/GlInvokers262$Texture.class"), "com/mojang/blaze3d/opengl/GlTexture");
        scanBytecode(root.resolve("mixin/GlInvokers262$Buffer.class"), "com/mojang/blaze3d/opengl/GlBuffer$Direct");
        scanBytecode(root.resolve("access/GlBufferDsaAccess262.class"), "lumin$getDsa");
        scanBytecode(root.resolve("mixin/GlBufferDsaMixin262.class"), "com/mojang/blaze3d/opengl/GlBuffer$Direct");
        scanBytecode(root.resolve("mixin/GlBufferDsaMixin262.class"), "dsa");
        scanBytecode(root.resolve("mixin/GlShaderBorrowedMixin262.class"), "shaderId");
        Path plugin = root.resolve("mixin/SmokeMixinConfigPlugin262.class");
        scanBytecode(plugin, "LUMIN_MC_SMOKE_MODE");
        scanBytecode(plugin, "missing-accessor");
        rejectBytecode(root.resolve("smoke/RealClientBridgeSmoke262.class"), "java/lang/reflect/Field");
        rejectBytecode(root.resolve("smoke/RealClientBridgeSmoke262.class"), "getDeclaredField");
        String mixinConfig = Files.readString(targetFile.resolveSibling("lumin-graphics-mc-262.mixins.json"));
        require(mixinConfig.contains("GlBufferDsaMixin262"), "DSA accessor mixin is not registered");
        require(mixinConfig.contains("SmokeMixinConfigPlugin262"), "smoke mixin plugin is not registered");
    }

    private static String readSource(Path reference, ZipFile jar, String relative) throws IOException {
        Path file = reference.resolve(relative.replace('/', java.io.File.separatorChar));
        if (Files.isRegularFile(file)) return Files.readString(file);
        var entry = jar.getEntry(relative);
        require(entry != null, "generated 26.2 source missing " + relative);
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void scanBytecode(Path classFile, String expected) throws IOException {
        String constants = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        require(constants.contains(expected), "compiled bytecode lacks " + expected + " in " + classFile);
    }

    private static void rejectBytecode(Path classFile, String forbidden) throws IOException {
        String constants = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
        require(!constants.contains(forbidden), "compiled bytecode contains forbidden " + forbidden + " in " + classFile);
    }

    private static String normalize(String value) {
        return value.replace("@org.jspecify.annotations.Nullable", "@Nullable").replaceAll("\\s+", "");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
