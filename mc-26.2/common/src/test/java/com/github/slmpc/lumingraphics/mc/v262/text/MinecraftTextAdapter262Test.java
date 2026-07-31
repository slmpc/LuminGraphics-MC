package com.github.slmpc.lumingraphics.mc.v262.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class MinecraftTextAdapter262Test {
    @Test void measuresUsingActual262FontGlyphContract() {
        BakedGlyph glyph = new BakedGlyph() {
            @Override public GlyphInfo info() { return GlyphInfo.simple(4.0F); }
            @Override public net.minecraft.client.gui.font.TextRenderable.Styled createGlyph(float x, float y,
                    int color, int shadowColor, net.minecraft.network.chat.Style style,
                    float boldOffset, float shadowOffset) { return null; }
        };
        GlyphSource source = new GlyphSource() {
            @Override public BakedGlyph getGlyph(int codepoint) { return glyph; }
            @Override public BakedGlyph getRandomGlyph(RandomSource random, int width) { return glyph; }
        };
        Font font = new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(net.minecraft.network.chat.FontDescription ignored) { return source; }
            @Override public EffectGlyph effect() { return null; }
        });
        MinecraftTextAdapter262 adapter = new MinecraftTextAdapter262(font);
        assertEquals(8.0F, adapter.measure("ab").width());
        assertEquals(18.0F, adapter.measure("a\nb").height());
        assertEquals(9, adapter.metrics().lineHeight());
        assertTrue(MinecraftTextAdapter262.SOURCE_PROVENANCE.contains("26.2"));
    }
}
