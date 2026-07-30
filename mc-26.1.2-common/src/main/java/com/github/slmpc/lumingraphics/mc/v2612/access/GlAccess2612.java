package com.github.slmpc.lumingraphics.mc.v2612.access;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class GlAccess2612 {
    private GlAccess2612() { }

    @Mixin(GlTexture.class)
    public interface Texture {
        @Invoker("<init>")
        static GlTexture create(int usage, String label, TextureFormat format, int width, int height,
                                int depthOrLayers, int mipLevels, int id) {
            throw new AssertionError();
        }
    }

    @Mixin(GlTextureView.class)
    public interface TextureView {
        @Invoker("<init>")
        static GlTextureView create(GlTexture texture, int baseMipLevel, int mipLevels) {
            throw new AssertionError();
        }
    }

    @Mixin(GlBuffer.class)
    public interface Buffer {
        @Accessor("handle") int lumin$handle();
        @Invoker("<init>")
        static GlBuffer create(@Nullable Supplier<String> label, DirectStateAccess dsa, int usage,
                               long size, int handle, @Nullable ByteBuffer persistentBuffer) {
            throw new AssertionError();
        }
    }

    @Mixin(GlShaderModule.class)
    public interface Shader {
        @Accessor("type") ShaderType lumin$type();
        @Invoker("<init>")
        static GlShaderModule create(int shaderId, Identifier id, ShaderType type) {
            throw new AssertionError();
        }
    }
}
