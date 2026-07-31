package com.github.slmpc.lumingraphics.mc.v262.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class GlInvokers262 {
    private GlInvokers262() { }

    @Mixin(GlTexture.class)
    public interface Texture {
        @Invoker("<init>")
        static GlTexture create(int usage, String label, GpuFormat format, int width, int height,
                                int depthOrLayers, int mipLevels, int id, FrameBufferCache cache) {
            throw new AssertionError("mixin invoker was not applied");
        }
    }

    @Mixin(GlTextureView.class)
    public interface TextureView {
        @Invoker("<init>")
        static GlTextureView create(GlTexture texture, int baseMipLevel, int mipLevels,
                                    FrameBufferCache cache) {
            throw new AssertionError("mixin invoker was not applied");
        }
    }

    @Mixin(GlBuffer.Direct.class)
    public interface Buffer {
        @Invoker("<init>")
        static GlBuffer.Direct create(DirectStateAccess dsa, int usage, long size, int handle,
                                      boolean canPersistentMap) {
            throw new AssertionError("mixin invoker was not applied");
        }
    }

    @Mixin(GlShaderModule.class)
    public interface Shader {
        @Invoker("<init>")
        static GlShaderModule create(int handle, Identifier id, ShaderType type) {
            throw new AssertionError("mixin invoker was not applied");
        }
    }

    @Mixin(GlProgram.class)
    public interface Program {
        @Invoker("<init>")
        static GlProgram create(int handle, String debugLabel) {
            throw new AssertionError("mixin invoker was not applied");
        }
    }

}
