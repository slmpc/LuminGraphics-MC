package com.github.slmpc.lumingraphics.mc.v262.access;

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

    public static GlObjectFactory262 factory() {
        return new GlObjectFactory262() {
            @Override public GlTexture texture(int usage, String label, GpuFormat format, int width,
                    int height, int depthOrLayers, int mipLevels, int handle, FrameBufferCache cache) {
                return borrowed(Texture.create(usage, label, format, width, height, depthOrLayers, mipLevels, handle, cache));
            }
            @Override public GlTextureView textureView(GlTexture texture, int baseMipLevel, int mipLevels,
                    FrameBufferCache cache) {
                return borrowed(TextureView.create(texture, baseMipLevel, mipLevels, cache));
            }
            @Override public GlBuffer.Direct buffer(DirectStateAccess dsa, int usage, long size, int handle,
                    boolean canPersistentMap) {
                return borrowed(Buffer.create(dsa, usage, size, handle, canPersistentMap));
            }
            @Override public GlShaderModule shader(int handle, Identifier id, ShaderType type) {
                return borrowed(Shader.create(handle, id, type));
            }
            @Override public GlProgram program(int handle, String debugLabel) {
                return borrowed(Program.create(handle, debugLabel));
            }
        };
    }

    private static <T> T borrowed(T value) {
        ((BorrowedGlObject262)value).luminGraphics$setBorrowed262(true);
        return value;
    }
}
