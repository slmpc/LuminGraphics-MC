package com.github.slmpc.lumingraphics.mc.v2612.text;

import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.gui.font.TextRenderable;

public interface TextRenderableAdapter extends TextRenderable {
    GpuSampler sampler();
}
