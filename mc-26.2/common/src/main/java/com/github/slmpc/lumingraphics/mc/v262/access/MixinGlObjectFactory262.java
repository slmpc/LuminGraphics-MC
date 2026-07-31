package com.github.slmpc.lumingraphics.mc.v262.access;

import com.github.slmpc.lumingraphics.mc.v262.mixin.GlInvokers262;
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

public final class MixinGlObjectFactory262 implements GlObjectFactory262 {
    @Override
    public GlTexture texture(int usage, String label, GpuFormat format, int width, int height,
                             int depthOrLayers, int mipLevels, int handle, FrameBufferCache cache) {
        return borrowed(GlInvokers262.Texture.create(
                usage, label, format, width, height, depthOrLayers, mipLevels, handle, cache));
    }

    @Override
    public GlTextureView textureView(GlTexture texture, int baseMipLevel, int mipLevels,
                                     FrameBufferCache cache) {
        return borrowed(GlInvokers262.TextureView.create(texture, baseMipLevel, mipLevels, cache));
    }

    @Override
    public GlBuffer.Direct buffer(DirectStateAccess dsa, int usage, long size, int handle,
                                  boolean canPersistentMap) {
        return borrowed(GlInvokers262.Buffer.create(dsa, usage, size, handle, canPersistentMap));
    }

    @Override
    public GlShaderModule shader(int handle, Identifier id, ShaderType type) {
        return borrowed(GlInvokers262.Shader.create(handle, id, type));
    }

    @Override
    public GlProgram program(int handle, String debugLabel) {
        return borrowed(GlInvokers262.Program.create(handle, debugLabel));
    }

    private static <T> T borrowed(T value) {
        ((BorrowedGlObject262) value).luminGraphics$setBorrowed262(true);
        return value;
    }
}
