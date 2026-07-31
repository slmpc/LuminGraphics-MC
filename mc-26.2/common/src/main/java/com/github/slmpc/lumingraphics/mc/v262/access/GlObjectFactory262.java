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

public interface GlObjectFactory262 {
    GlTexture texture(int usage, String label, GpuFormat format, int width, int height,
                      int depthOrLayers, int mipLevels, int handle, FrameBufferCache frameBufferCache);

    GlTextureView textureView(GlTexture texture, int baseMipLevel, int mipLevels,
                              FrameBufferCache frameBufferCache);

    GlBuffer.Direct buffer(DirectStateAccess dsa, int usage, long size, int handle,
                           boolean canPersistentMap);

    GlShaderModule shader(int handle, Identifier id, ShaderType type);

    GlProgram program(int handle, String debugLabel);
}
