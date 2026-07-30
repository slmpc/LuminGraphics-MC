package com.github.slmpc.lumingraphics.mc.v2612.text;

import com.github.slmpc.lumingraphics.text.FontLoader;
import java.util.Objects;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

public final class FontMetricsAdapter {
    public static final float VANILLA_LINE_HEIGHT = 9.0f;
    public static final float SPACE_WIDTH = 3.0f;

    private final FontLoader font;
    private final float scale;

    public FontMetricsAdapter(FontLoader font) {
        this.font = Objects.requireNonNull(font, "font");
        this.scale = VANILLA_LINE_HEIGHT / font.metrics().lineHeight();
    }

    public float advance(int codepoint, Style style) {
        Objects.requireNonNull(style, "style");
        float base = Character.isWhitespace(codepoint) ? SPACE_WIDTH : font.advance(codepoint) * scale;
        return base + (style.isBold() ? 1.0f : 0.0f);
    }

    public float width(String text) {
        float[] width = {0.0f};
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (position, style, codepoint) -> {
            width[0] += advance(codepoint, style);
            return true;
        });
        return width[0];
    }

    public float width(FormattedCharSequence text) {
        float[] width = {0.0f};
        text.accept((position, style, codepoint) -> {
            width[0] += advance(codepoint, style);
            return true;
        });
        return width[0];
    }

    public float width(FormattedText text) {
        float[] width = {0.0f};
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (position, style, codepoint) -> {
            width[0] += advance(codepoint, style);
            return true;
        });
        return width[0];
    }
}
