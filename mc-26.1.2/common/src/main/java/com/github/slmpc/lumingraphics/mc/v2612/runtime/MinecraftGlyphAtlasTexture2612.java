package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/** 同一 glyph atlas 在 Lumin 2D 与 Minecraft GUI 路径中的借用视图。 */
public record MinecraftGlyphAtlasTexture2612(
        Render2DTexture luminTexture,
        GlTextureView minecraftTextureView,
        Identifier minecraftId,
        GpuSampler sampler
) {
    public MinecraftGlyphAtlasTexture2612 {
        Objects.requireNonNull(luminTexture, "luminTexture");
        Objects.requireNonNull(minecraftTextureView, "minecraftTextureView");
        Objects.requireNonNull(minecraftId, "minecraftId");
        Objects.requireNonNull(sampler, "sampler");
    }
}
