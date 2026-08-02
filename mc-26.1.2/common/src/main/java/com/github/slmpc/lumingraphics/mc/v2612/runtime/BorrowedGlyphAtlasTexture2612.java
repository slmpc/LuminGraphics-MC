package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Objects;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** Minecraft 只借用 Prism 图集句柄，实际释放由 glyph atlas upload 管理。 */
final class BorrowedGlyphAtlasTexture2612 extends AbstractTexture {
    BorrowedGlyphAtlasTexture2612(GpuTexture texture, GpuTextureView textureView, GpuSampler sampler) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.textureView = Objects.requireNonNull(textureView, "textureView");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
    }

    @Override public void close() { }
}
