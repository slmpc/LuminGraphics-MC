package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.slmpc.lumingraphics.mc.v2612.access.GlAccess2612;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AccessBytecode2612Test {
    @Test
    void genuine2612TargetsAndCompiledDucksHaveExactJvmDescriptors() throws Exception {
        Shape texture = shape(GlTexture.class);
        assertTrue(texture.methods.contains("<init>(ILjava/lang/String;Lcom/mojang/blaze3d/textures/TextureFormat;IIIII)V"));
        assertTrue(texture.methods.contains("close()V"));
        assertTrue(texture.methods.contains("removeViews()V"));
        assertTrue(texture.fields.contains("id:I"));
        assertTrue(texture.fields.contains("closed:Z"));

        assertTrue(shape(GlTextureView.class).methods.contains(
                "<init>(Lcom/mojang/blaze3d/opengl/GlTexture;II)V"));

        Shape buffer = shape(GlBuffer.class);
        assertTrue(buffer.methods.contains(
                "<init>(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/opengl/DirectStateAccess;IJILjava/nio/ByteBuffer;)V"));
        assertTrue(buffer.fields.contains("handle:I"));
        assertTrue(shape(GlShaderModule.class).methods.contains(
                "<init>(ILnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)V"));
        assertTrue(shape(GlProgram.class).methods.contains("close()V"));

        Shape textureDuck = shape(GlAccess2612.Texture.class);
        assertTrue(textureDuck.methods.contains(
                "create(ILjava/lang/String;Lcom/mojang/blaze3d/textures/TextureFormat;IIIII)Lcom/mojang/blaze3d/opengl/GlTexture;"));
        assertTrue(shape(GlAccess2612.TextureView.class).methods.contains(
                "create(Lcom/mojang/blaze3d/opengl/GlTexture;II)Lcom/mojang/blaze3d/opengl/GlTextureView;"));
        Shape bufferDuck = shape(GlAccess2612.Buffer.class);
        assertTrue(bufferDuck.methods.contains("lumin$handle()I"));
        assertTrue(bufferDuck.methods.contains(
                "create(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/opengl/DirectStateAccess;IJILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/opengl/GlBuffer;"));
        Shape shaderDuck = shape(GlAccess2612.Shader.class);
        assertTrue(shaderDuck.methods.contains("lumin$type()Lcom/mojang/blaze3d/shaders/ShaderType;"));
        assertTrue(shaderDuck.methods.contains(
                "create(ILnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Lcom/mojang/blaze3d/opengl/GlShaderModule;"));
    }

    @Test
    void compiledTextureMixinCarriesCloseAndDestroyInjectionAnnotations() throws Exception {
        Shape mixin = shape("com.github.slmpc.lumingraphics.mc.v2612.mixin.GlTextureBorrowedMixin");
        assertEquals(Set.of(
                "lumin$closeBorrowed(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
                "lumin$skipBorrowedNativeDelete(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                mixin.injectMethods);
        assertEquals(Set.of("lumin$closeBorrowed(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                shape("com.github.slmpc.lumingraphics.mc.v2612.mixin.GlBufferBorrowedMixin").injectMethods);
        assertEquals(Set.of("lumin$closeBorrowed(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                shape("com.github.slmpc.lumingraphics.mc.v2612.mixin.GlShaderModuleBorrowedMixin").injectMethods);
        assertEquals(Set.of("lumin$closeBorrowed(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"),
                shape("com.github.slmpc.lumingraphics.mc.v2612.mixin.GlProgramBorrowedMixin").injectMethods);
    }

    private static Shape shape(Class<?> type) throws IOException {
        return shape(type.getName());
    }

    private static Shape shape(String binaryName) throws IOException {
        Shape shape = new Shape();
        String resourceName = binaryName.replace('.', '/') + ".class";
        try (var input = AccessBytecode2612Test.class.getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(input, "missing compiled class resource " + resourceName);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                          String signature, Object value) {
                    shape.fields.add(name + ':' + descriptor);
                    return null;
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                            String signature, String[] exceptions) {
                    String method = name + descriptor;
                    shape.methods.add(method);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (descriptor.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                                shape.injectMethods.add(method);
                            }
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        }
        return shape;
    }

    private static final class Shape {
        private final Set<String> fields = new HashSet<>();
        private final Set<String> methods = new HashSet<>();
        private final Set<String> injectMethods = new HashSet<>();
    }
}
