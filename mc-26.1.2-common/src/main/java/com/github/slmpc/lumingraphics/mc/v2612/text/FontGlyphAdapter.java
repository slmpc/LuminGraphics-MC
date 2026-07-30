package com.github.slmpc.lumingraphics.mc.v2612.text;

import com.mojang.blaze3d.font.GlyphInfo;
import java.util.Objects;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

public final class FontGlyphAdapter implements BakedGlyph {
    @FunctionalInterface
    public interface GlyphFactory {
        TextRenderable.@Nullable Styled create(float x, float y, int color, int shadowColor,
                                               Style style, float boldOffset, float shadowOffset);
    }

    private final GlyphInfo info;
    private final GlyphFactory factory;

    public FontGlyphAdapter(float advance, GlyphFactory factory) {
        this.info = GlyphInfo.simple(advance);
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override public GlyphInfo info() { return info; }

    @Override
    public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor,
                                                        Style style, float boldOffset, float shadowOffset) {
        return factory.create(x, y, color, shadowColor, style, boldOffset, shadowOffset);
    }
}
