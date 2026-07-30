package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.slmpc.lumingraphics.mc.v2612.text.FontGlyphAdapter;
import com.github.slmpc.lumingraphics.mc.v2612.text.FontMetricsAdapter;
import com.github.slmpc.lumingraphics.text.FontLoader;
import com.github.slmpc.lumingraphics.text.FontMetrics;
import com.github.slmpc.lumingraphics.text.GlyphDescriptor;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class Metadata2612Test {
    @Test
    void textureFormatsAreExhaustiveAndRejectUnrepresentableValues() {
        assertEquals(RhiFormat.RGBA8_UNORM, Blaze3DBridge2612.toRhiFormat(TextureFormat.RGBA8));
        assertEquals(RhiFormat.R8_UNORM, Blaze3DBridge2612.toRhiFormat(TextureFormat.RED8));
        assertEquals(RhiFormat.D32_FLOAT, Blaze3DBridge2612.toRhiFormat(TextureFormat.DEPTH32));
        assertThrows(IllegalArgumentException.class,
                () -> Blaze3DBridge2612.toRhiFormat(TextureFormat.RED8I));
        assertThrows(IllegalArgumentException.class,
                () -> Blaze3DBridge2612.toBlazeFormat(RhiFormat.BGRA8_UNORM));
    }

    @Test
    void minecraftMetricsPreserveWhitespaceBoldAndFontScale() {
        FontMetricsAdapter metrics = new FontMetricsAdapter(new StubFont());
        assertEquals(3.0f, metrics.advance(' ', Style.EMPTY));
        assertEquals(4.0f, metrics.advance(' ', Style.EMPTY.withBold(true)));
        assertEquals(4.5f, metrics.advance('A', Style.EMPTY));
        assertEquals(7.5f, metrics.width("A "));
    }

    @Test
    void glyphAdapterForwardsMinecraftRenderStateToTheFactory() {
        AtomicReference<GlyphCall> call = new AtomicReference<>();
        FontGlyphAdapter glyph = new FontGlyphAdapter(4.5f, (x, y, color, shadowColor, style,
                                                            boldOffset, shadowOffset) -> {
            call.set(new GlyphCall(x, y, color, shadowColor, style, boldOffset, shadowOffset));
            return null;
        });
        Style style = Style.EMPTY.withBold(true);

        assertNull(glyph.createGlyph(1.0f, 2.0f, 0x112233, 0x445566, style, 0.5f, 1.0f));
        assertEquals(4.5f, glyph.info().getAdvance());
        assertEquals(new GlyphCall(1.0f, 2.0f, 0x112233, 0x445566, style, 0.5f, 1.0f), call.get());
    }

    private record GlyphCall(float x, float y, int color, int shadowColor, Style style,
                             float boldOffset, float shadowOffset) { }

    private static final class StubFont implements FontLoader {
        @Override public int advance(int codepoint) { return 10; }
        @Override public FontMetrics metrics() { return new FontMetrics(7, 2, 0, 20); }
        @Override public CompletableFuture<GlyphDescriptor> requestGlyph(int codepoint) { throw new UnsupportedOperationException(); }
        @Override public GlyphDescriptor requireGlyph(int codepoint) { throw new UnsupportedOperationException(); }
        @Override public int kerning(int left, int right) { return 0; }
        @Override public long glyphRevision() { return 0; }
        @Override public long atlasRevision() { return 0; }
        @Override public void close() { }
    }
}
